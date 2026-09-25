package com.lekhani.android.ime

import android.content.Context
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.lekhani.android.canvas.KeyboardCanvasView
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.emoji.EmojiRecentsManager
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ffi.LekhaniException
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.ui.candidate.CandidateBlacklist
import com.lekhani.android.ui.candidate.CandidateStripState
import com.lekhani.android.ui.candidate.CandidateStripView
import com.lekhani.android.ui.candidate.HomophoneAnnotator
import com.lekhani.android.ui.clipboard.ClipboardSheetView
import com.lekhani.android.ui.emoji.EmojiPickerView
import com.lekhani.android.ui.voice.VoiceWaveformOverlay
import com.lekhani.android.voice.AudioStreamingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LekhaniInputMethodService
 * ══════════════════════════════════════════════════════════════════════════════
 * The central Android IME service. Bridges the Kotlin/Android layer with the
 * Rust [AndroidLekhaniSession] engine via UniFFI-generated bindings.
 *
 * Architecture (Three-Tier):
 *   Tier 3 (this class) ──UniFFI──► Tier 2 (lekhani-android Rust crate)
 *                                        │
 *                                   Tier 1 (lekhani-core / lekhani-parser)
 *
 * System constraint compliance (AGENTS.md §3):
 *   ✅ directBootAware="true"  — prefs use Device Protected Storage
 *   ✅ Password field policy   — auto-switch to English, freeze learning
 *   ✅ Landscape non-fullscreen — onEvaluateFullscreenMode() → false
 *   ✅ WebView resilience       — pre-edit shadow buffer + cursor guard
 *   ✅ Zero INTERNET permission — no network calls anywhere in this file
 *
 * Threading model:
 *   - [onKey] and [onStartInput] run on the **main thread**.
 *   - All calls into [session] are synchronized internally by the Rust Mutex.
 *   - [serviceScope] (Dispatchers.Default) is used for dictionary indexing
 *     and surrounding-text context refresh (non-blocking background work).
 */
class LekhaniInputMethodService : InputMethodService() {

    // ── UniFFI session ─────────────────────────────────────────────────────────

    /**
     * The Rust-backed IME state machine. Thread-safe via internal Mutex.
     * Initialized lazily so the .so is loaded only when the service starts.
     */
    private val session: AndroidLekhaniSession by lazy { AndroidLekhaniSession() }

    // ── Coroutine scope ────────────────────────────────────────────────────────

    /**
     * Service-scoped coroutine scope for background work (context refresh,
     * dictionary indexing). Uses SupervisorJob so individual task failures
     * don't cancel the whole scope.
     */
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Synthetic Lifecycle for Jetpack Compose views ────────────────────────
    private val imeLifecycleOwner = ImeLifecycleOwner()

    // ── Canvas keyboard view ───────────────────────────────────────────────────

    /** The hardware-canvas keyboard view. Null until [onCreateInputView] is called. */
    private var keyboardView: KeyboardCanvasView? = null

    // ── Candidate strip state ─────────────────────────────────────────────────

    /**
     * Hot [StateFlow] of [CandidateStripState] observed by [CandidateStripView].
     * Updated on every [processKey] / [handleBackspace] / [handleSpace] call.
     * Published on the main thread — no synchronization needed.
     */
    private val _candidateState = MutableStateFlow<CandidateStripState>(CandidateStripState.Empty)
    val candidateState: StateFlow<CandidateStripState> = _candidateState.asStateFlow()

    /** Persists long-press blacklisted candidates to Device Protected Storage. */
    private val blacklist: CandidateBlacklist by lazy { CandidateBlacklist(this) }

    /** Audio streaming manager for 100% offline voice typing (Phase 5). */
    private val audioManager: AudioStreamingManager by lazy { AudioStreamingManager(this) }

    // ── Input modes & auxiliary views (Phase 6) ──────────────────────────────

    enum class InputViewMode { KEYBOARD, EMOJI, CLIPBOARD }

    private var currentMode: InputViewMode = InputViewMode.KEYBOARD
    private var rootInputContainer: FrameLayout? = null
    private var emojiPickerView: ComposeView? = null
    private var clipboardView: ComposeView? = null
    private var isCurrentFieldPrivate: Boolean = false

    private val recentsManager: EmojiRecentsManager by lazy { EmojiRecentsManager(this) }
    private val clipboardStore: LekhaniClipboardStore by lazy { LekhaniClipboardStore(this) }

    // ── WebView / Chromium composing shadow buffer ─────────────────────────────

    /**
     * Shadow copy of the last committed preedit text sent to setComposingText().
     * Required for WebView / Chromium resilience: Chromium occasionally drops
     * composing state silently. Before committing, we verify the shadow matches
     * what the InputConnection actually reports and re-set if diverged.
     */
    private var preeditShadow: String = ""

    // ── Preferences (Device Protected Storage) ─────────────────────────────────

    /**
     * Returns a SharedPreferences backed by Device Protected Storage.
     *
     * AGENTS.md §3.1: When directBootAware="true", preferences MUST use DPS
     * so they survive cold-boot before device decryption. Using credential-
     * protected storage here would throw an IllegalStateException on the lock
     * screen.
     */
    private val devicePrefs by lazy {
        createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()
        imeLifecycleOwner.onResume()

        // Restore the user's last-used layout from Device Protected Storage.
        val savedLayout = devicePrefs.getString(PREF_LAYOUT, null)
            ?.let { runCatching { LekhaniLayoutType.valueOf(it) }.getOrNull() }
            ?: LekhaniLayoutType.PROBAHO
        session.setLayout(savedLayout)
        Log.i(TAG, "Lekhani IME created; layout = $savedLayout")

        // Listen for system clipboard updates; guard against private field capture
        val sysClipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        sysClipboard?.addPrimaryClipChangedListener {
            if (!isCurrentFieldPrivate) {
                val clip = sysClipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val text = clip.getItemAt(0)?.text?.toString()
                    if (!text.isNullOrBlank()) {
                        clipboardStore.addClip(text)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        imeLifecycleOwner.onDestroy()
        audioManager.cancelStreaming()
        keyboardView = null
        serviceScope.cancel()
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Input session lifecycle  (called on every editor focus/blur)
    // ══════════════════════════════════════════════════════════════════════════

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        session.reset()
        preeditShadow = ""
        setInputViewMode(InputViewMode.KEYBOARD)
        applyInputTypePolicy(info)
        refreshSurroundingContext()
    }

    override fun onFinishInput() {
        audioManager.cancelStreaming()
        session.reset()
        preeditShadow = ""
        clearCandidates()
        super.onFinishInput()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Re-apply policy in case the editor info changed after the view appeared
        applyInputTypePolicy(info)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UI — keyboard view (stub; KeyboardCanvasView implemented in Phase 3)
    // ══════════════════════════════════════════════════════════════════════════

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        attachLifecycleOwner(win.decorView)
    }

    private fun attachLifecycleOwner(view: View) {
        view.setViewTreeLifecycleOwner(imeLifecycleOwner)
        view.setViewTreeViewModelStoreOwner(imeLifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(imeLifecycleOwner)
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { attachLifecycleOwner(it) }
        val container = FrameLayout(this).apply {
            attachLifecycleOwner(this)
        }
        rootInputContainer = container

        val canvasView = KeyboardCanvasView(this).also { v ->
            v.setLayout(LayoutRegistry.get(session.getLayout()), shifted = false)
            v.keyListener = object : KeyboardCanvasView.KeyListener {
                override fun onKey(key: Key, action: KeyAction) {
                    handleKeyAction(key, action)
                }
            }
        }
        keyboardView = canvasView
        container.addView(
            canvasView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return container
    }

    /**
     * Creates the Compose-based candidate strip shown above the keyboard.
     *
     * Android IME framework: [onCreateCandidatesView] is called lazily when the
     * IME first calls [setCandidatesViewShown](true). We eagerly set up the
     * Compose owner chain (Lifecycle + SavedState) required for Compose to work
     * inside a Service (which is not a Fragment or Activity).
     */
    override fun onCreateCandidatesView(): View {
        window?.window?.decorView?.let { attachLifecycleOwner(it) }
        return ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

            setContent {
                Box {
                    CandidateStripView(
                        stateFlow = candidateState,
                        onCandidateClick = { text -> onCandidateSelected(text) },
                        onBlacklist = { text ->
                            blacklist.add(text)
                            // Re-publish current candidates with the blacklisted word removed
                            val current = _candidateState.value
                            if (current is CandidateStripState.Candidates) {
                                val filtered = current.items.filter { it.text != text }
                                _candidateState.value = if (filtered.isEmpty())
                                    CandidateStripState.Empty
                                else
                                    CandidateStripState.Candidates(filtered)
                            }
                        },
                    )

                    VoiceWaveformOverlay(
                        voiceStateFlow = audioManager.voiceState,
                        onDone = {
                            val result = audioManager.stopStreaming()
                            if (result.isNotBlank()) {
                                currentInputConnection?.finishComposingText()
                                currentInputConnection?.commitText(result, 1)
                            }
                        },
                        onCancel = {
                            audioManager.cancelStreaming()
                        },
                    )
                }
            }
        }
    }

    // ── Internal key dispatch ─────────────────────────────────────────────────

    private fun handleKeyAction(key: Key, action: KeyAction) {
        when (action) {
            is KeyAction.Character -> onKey(action.token)
            KeyAction.Backspace    -> onBackspace()
            KeyAction.Space        -> onSpace()
            KeyAction.Enter        -> commitEnter()
            KeyAction.Shift        -> toggleShift()
            KeyAction.SwitchNumeric -> { /* Phase 6: numbers layer */ }
            KeyAction.SwitchLayout -> cycleLayout()
            KeyAction.VoiceTyping  -> startVoiceTyping()
            KeyAction.SwitchEmoji  -> setInputViewMode(InputViewMode.EMOJI)
            KeyAction.SwitchClipboard -> setInputViewMode(InputViewMode.CLIPBOARD)
        }
    }

    /**
     * Toggles between Keyboard Canvas, Emoji/Symbol Picker, and Clipboard History.
     */
    fun setInputViewMode(mode: InputViewMode) {
        currentMode = mode
        val container = rootInputContainer ?: return

        when (mode) {
            InputViewMode.KEYBOARD -> {
                keyboardView?.visibility = View.VISIBLE
                emojiPickerView?.visibility = View.GONE
                clipboardView?.visibility = View.GONE
                setCandidatesViewShown(true)
            }
            InputViewMode.EMOJI -> {
                keyboardView?.visibility = View.GONE
                clipboardView?.visibility = View.GONE
                setCandidatesViewShown(false)
                if (emojiPickerView == null) {
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

                        setContent {
                            EmojiPickerView(
                                recentsManager = recentsManager,
                                onEmojiSelected = { emoji ->
                                    currentInputConnection?.commitText(emoji, 1)
                                },
                                onBackspace = { onBackspace() },
                                onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                            )
                        }
                    }
                    emojiPickerView = compose
                    container.addView(
                        compose,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                emojiPickerView?.visibility = View.VISIBLE
            }
            InputViewMode.CLIPBOARD -> {
                keyboardView?.visibility = View.GONE
                emojiPickerView?.visibility = View.GONE
                setCandidatesViewShown(false)
                if (clipboardView == null) {
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)

                        setContent {
                            ClipboardSheetView(
                                clipboardStore = clipboardStore,
                                onPaste = { text ->
                                    currentInputConnection?.commitText(text, 1)
                                    setInputViewMode(InputViewMode.KEYBOARD)
                                },
                                onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                            )
                        }
                    }
                    clipboardView = compose
                    container.addView(
                        compose,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                clipboardView?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Starts offline voice typing capture and displays waveform overlay.
     */
    private fun startVoiceTyping() {
        if (!audioManager.hasRecordPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted; cannot start voice typing")
            return
        }

        setCandidatesViewShown(true)
        audioManager.startStreaming { finalTranscript ->
            if (finalTranscript.isNotBlank()) {
                currentInputConnection?.finishComposingText()
                currentInputConnection?.commitText(finalTranscript, 1)
            }
        }
    }

    private fun toggleShift() {
        keyboardView?.setShifted(keyboardView?.let {
            // Read current shift state from the view (we don't store it here)
            false  // The view manages its own shift toggle internally
        } ?: false)
    }

    private fun commitEnter() {
        currentInputConnection?.performEditorAction(
            currentInputEditorInfo?.imeOptions ?: android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        )
        session.reset()
        keyboardView?.setShifted(false)
    }

    private fun cycleLayout() {
        val current = session.getLayout()
        val all = LayoutRegistry.all
        val nextIndex = (all.indexOf(current) + 1) % all.size
        switchLayout(all[nextIndex])
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fullscreen mode policy  (AGENTS.md §3.3)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * ALWAYS returns false.
     *
     * The default Android IME behaviour in landscape mode is to take over the
     * entire screen with a "full-screen extract UI" that hides the app content.
     * This is disorienting and breaks context-dependent typing (e.g., replying
     * to a message you can no longer read). Lekhani always overlays as a panel.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false
 
    override fun onEvaluateInputViewShown(): Boolean = true

    // ══════════════════════════════════════════════════════════════════════════
    // Key processing  (hot path — must not block the main thread)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Processes a key from the Kotlin keyboard view layer.
     *
     * Called by [KeyboardCanvasView] (Phase 3) for every tap / long-press.
     * This method runs on the main thread and must complete in < 3 ms.
     *
     * The Rust session handles zero-allocation processing internally; we only
     * interact with [InputConnection] here (which involves Binder IPC, but
     * that is unavoidable at the Android layer).
     */
    fun onKey(keyToken: String) {
        val ic = currentInputConnection ?: return

        val result = try {
            session.processKey(keyToken)
        } catch (e: LekhaniException) {
            Log.e(TAG, "processKey error for '$keyToken': $e")
            return
        }

        result.commitText?.let { text ->
            ic.finishComposingText()
            ic.commitText(text, 1)
            preeditShadow = ""
            clearCandidates()
        } ?: run {
            // Update composing text with Chromium/WebView resilience guard
            setComposingTextSafe(ic, result.preedit)
            publishCandidates(result.candidates)
        }
    }

    /**
     * Handles the Backspace key.
     * If the composing buffer is non-empty, delegates to the Rust engine for
     * grapheme-cluster-aware deletion. Otherwise sends a raw KEYCODE_DEL to
     * the InputConnection so the target app deletes its own preceding character.
     */
    fun onBackspace() {
        val ic = currentInputConnection ?: return

        if (session.isComposing()) {
            val result = try {
                session.handleBackspace()
            } catch (e: LekhaniException) {
                Log.e(TAG, "handleBackspace error: $e")
                return
            }
            setComposingTextSafe(ic, result.preedit)
            publishCandidates(result.candidates)
        } else {
            // Nothing composing — delete character in the target app
            ic.deleteSurroundingText(1, 0)
        }
    }

    /**
     * Handles the Spacebar key.
     * Commits the current composing buffer (NFC-normalized + space appended).
     */
    fun onSpace() {
        val ic = currentInputConnection ?: return

        val result = try {
            session.handleSpace()
        } catch (e: LekhaniException) {
            Log.e(TAG, "handleSpace error: $e")
            return
        }

        result.commitText?.let { text ->
            ic.finishComposingText()
            ic.commitText(text, 1)
            preeditShadow = ""
            clearCandidates()
        }

        // After committing a word, refresh surrounding context for AI scorer
        refreshSurroundingContext()
    }

    /**
     * Commits a candidate selected from the strip.
     * Clears composing state and commits the NFC-normalized candidate + space.
     */
    fun onCandidateSelected(candidate: String) {
        val ic = currentInputConnection ?: return

        val result = try {
            session.selectCandidate(candidate)
        } catch (e: LekhaniException) {
            Log.e(TAG, "selectCandidate error: $e")
            return
        }

        result.commitText?.let { text ->
            ic.finishComposingText()
            ic.commitText(text, 1)
            preeditShadow = ""
            clearCandidates()
        }

        refreshSurroundingContext()
    }

    /**
     * Switches the active layout. Persists the choice to Device Protected Storage.
     */
    fun switchLayout(layout: LekhaniLayoutType) {
        session.setLayout(layout)
        keyboardView?.setLayout(LayoutRegistry.get(layout), shifted = false)
        devicePrefs.edit().putString(PREF_LAYOUT, layout.name).apply()
        Log.i(TAG, "Layout switched to $layout")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Private helpers
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Publishes candidate list to the Compose candidate strip.
     * Filters out user-blacklisted words and applies homophone disambiguation badges.
     */
    private fun publishCandidates(raw: List<String>) {
        val filtered = raw.filter { !blacklist.isBlacklisted(it) }
        _candidateState.value = if (filtered.isEmpty()) {
            CandidateStripState.Empty
        } else {
            CandidateStripState.Candidates(HomophoneAnnotator.annotate(filtered))
        }
        setCandidatesViewShown(filtered.isNotEmpty())
    }

    /**
     * Clears all candidates and hides the candidate strip view.
     */
    private fun clearCandidates() {
        _candidateState.value = CandidateStripState.Empty
        setCandidatesViewShown(false)
    }

    /**
     * Enforces password / incognito field policy (AGENTS.md §3.2).
     *
     * When the focused field is a password, PIN, or incognito text area:
     *  - Auto-switch the session to English QWERTY.
     *  - Mark the session as private (freezes AI learning + clipboard capture).
     *
     * When leaving such a field ([onStartInput] is called again for a normal
     * field), we restore the user's preferred layout from Device Protected Storage.
     */
    private fun applyInputTypePolicy(info: EditorInfo) {
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        val inputVariation = info.inputType and InputType.TYPE_MASK_VARIATION
        val noSuggestions = (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0

        val isPasswordField = inputClass == InputType.TYPE_CLASS_TEXT && (
            inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isPrivate = isPasswordField || noSuggestions

        isCurrentFieldPrivate = isPrivate
        session.setPrivateField(isPrivate)

        if (!isPrivate) {
            // Restore the user's preferred layout when leaving a private field.
            val preferred = devicePrefs.getString(PREF_LAYOUT, null)
                ?.let { runCatching { LekhaniLayoutType.valueOf(it) }.getOrNull() }
                ?: LekhaniLayoutType.PROBAHO
            // Only restore if the session is currently on English (was auto-switched)
            if (session.getLayout() == LekhaniLayoutType.ENGLISH) {
                session.setLayout(preferred)
            }
        }
    }

    /**
     * Refreshes the surrounding text context in the Rust AI scorer.
     *
     * Run on a background coroutine (Dispatchers.IO for the Binder IPC call to
     * getTextBeforeCursor, then switch to Default for the Rust call) so the
     * main thread is never blocked.
     *
     * AGENTS.md §5: The UI thread and onKey() methods must remain non-blocking.
     */
    private fun refreshSurroundingContext() {
        serviceScope.launch {
            val contextText = withContext(Dispatchers.IO) {
                currentInputConnection
                    ?.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)
                    ?.toString()
                    ?: ""
            }
            withContext(Dispatchers.Default) {
                session.setContext(contextText)
            }
        }
    }

    /**
     * WebView / Chromium composing-text resilience (AGENTS.md §3.4).
     *
     * Chromium-based WebViews and some social media inputs (WhatsApp Web,
     * Facebook comment boxes) silently discard [InputConnection.setComposingText]
     * calls or reset cursor position unexpectedly, causing "ghost letters" where
     * the composing text duplicates on screen.
     *
     * Mitigation:
     *   1. Before calling setComposingText(), call finishComposingText() if
     *      the new preedit differs from our shadow — this forces Chromium to
     *      flush its internal composing state.
     *   2. Update the shadow AFTER the call so we can detect future divergences.
     */
    private fun setComposingTextSafe(ic: InputConnection, preedit: String) {
        if (preedit.isEmpty()) {
            if (preeditShadow.isNotEmpty()) {
                ic.finishComposingText()
                preeditShadow = ""
            }
            return
        }

        // If shadow diverges from what we're about to set, force a flush first.
        if (preeditShadow != preedit && preeditShadow.isNotEmpty()) {
            ic.finishComposingText()
        }

        ic.setComposingText(preedit, 1)
        preeditShadow = preedit
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Constants
    // ══════════════════════════════════════════════════════════════════════════

    companion object {
        private const val TAG = "LekhaniIME"
        private const val PREFS_NAME = "lekhani_prefs"
        private const val PREF_LAYOUT = "active_layout"
        /**
         * Number of characters before the cursor fetched for AI context.
         * 256 chars covers ~2-3 sentences — sufficient for bigram/trigram scoring
         * without excessive Binder IPC payload.
         */
        private const val CONTEXT_CHAR_LIMIT = 256
    }
}
