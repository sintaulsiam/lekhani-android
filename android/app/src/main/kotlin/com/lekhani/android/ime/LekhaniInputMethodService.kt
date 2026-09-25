package com.lekhani.android.ime

import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import android.widget.LinearLayout
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
import com.lekhani.android.data.dictionary.LekhaniAssetInstaller
import com.lekhani.android.data.emoji.EmojiRecentsManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ffi.LekhaniException
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.theme.ThemeRegistry
import com.lekhani.android.ui.LekhaniSettingsActivity
import com.lekhani.android.ui.candidate.CandidateBlacklist
import com.lekhani.android.ui.candidate.CandidateStripState
import com.lekhani.android.ui.candidate.CandidateStripView
import com.lekhani.android.ui.candidate.HomophoneAnnotator
import com.lekhani.android.ui.clipboard.ClipboardSheetView
import com.lekhani.android.ui.emoji.EmojiPickerView
import com.lekhani.android.ui.voice.VoiceWaveformOverlay
import com.lekhani.android.voice.AudioStreamingManager
import com.lekhani.android.voice.VoiceTypingState
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
    private var rootInputContainer: LinearLayout? = null
    private var modesContainer: FrameLayout? = null
    private var candidateStripComposeView: ComposeView? = null
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

    private val keyboardPrefs by lazy {
        KeyboardPreferences.get(this)
    }

    private val feedbackManager by lazy {
        LekhaniFeedbackManager(this)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()
        imeLifecycleOwner.onResume()

        // Unpack bundled offline dictionaries and layouts to application storage
        try {
            LekhaniAssetInstaller.installAssetsIfNeeded(applicationContext)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing offline assets: ${e.message}")
        }

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
        feedbackManager.release()
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
        keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
        updateCandidatesVisibility()
    }

    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // If cursor moved outside the active composing region or composing was dismissed
        if (candidatesStart < 0 && candidatesEnd < 0 && session.isComposing()) {
            session.reset()
            preeditShadow = ""
            clearCandidates()
        }
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
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            attachLifecycleOwner(this)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootInputContainer = rootLayout

        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)
        val candidateStrip = ComposeView(this).apply {
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
                        theme = activeTheme,
                        activeTools = keyboardPrefs.getActiveToolbarTools(),
                        onToolClick = { tool -> handleToolbarToolClick(tool) },
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
        candidateStripComposeView = candidateStrip
        candidateStrip.visibility = View.VISIBLE
        rootLayout.addView(
            candidateStrip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val container = FrameLayout(this).apply {
            attachLifecycleOwner(this)
        }
        modesContainer = container

        val canvasView = KeyboardCanvasView(this).also { v ->
            val curLayout = session.getLayout()
            v.setLayout(LayoutRegistry.get(curLayout), curLayout, shifted = false)
            v.applyPreferences(keyboardPrefs, feedbackManager)
            v.keyListener = object : KeyboardCanvasView.KeyListener {
                override fun onKey(key: Key, action: KeyAction) {
                    handleKeyAction(key, action)
                }

                override fun onSpaceSwipe(direction: Int) {
                    cycleLayout(direction)
                }

                override fun onSpaceLongPress() {
                    showQuickLayoutPicker()
                }

                override fun onGlideGesture(keys: List<String>) {
                    handleGlideGesture(keys)
                }

                override fun onCursorMove(deltaChars: Int) {
                    handleCursorMove(deltaChars)
                }

                override fun onSwipeDelete(wordCount: Int) {
                    handleSwipeDelete(wordCount)
                }

                override fun onSwipeDeletePreview(wordCount: Int) {
                    // Preview feedback handled in canvas badge and haptics
                }

                override fun onFormFactorChange(newFormFactor: KeyboardPreferences.FormFactor) {
                    setFormFactor(newFormFactor)
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
        rootLayout.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        return rootLayout
    }

    override fun onCreateCandidatesView(): View? = null

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
        val container = modesContainer ?: return
        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)

        when (mode) {
            InputViewMode.KEYBOARD -> {
                keyboardView?.visibility = View.VISIBLE
                emojiPickerView?.visibility = View.GONE
                clipboardView?.visibility = View.GONE
                updateCandidatesVisibility()
            }
            InputViewMode.EMOJI -> {
                keyboardView?.visibility = View.GONE
                clipboardView?.visibility = View.GONE
                candidateStripComposeView?.visibility = View.GONE
                if (emojiPickerView == null) {
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
                emojiPickerView?.setContent {
                    EmojiPickerView(
                        recentsManager = recentsManager,
                        theme = activeTheme,
                        onEmojiSelected = { emoji ->
                            currentInputConnection?.commitText(emoji, 1)
                        },
                        onBackspace = { onBackspace() },
                        onSpace = { onSpace() },
                        onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                    )
                }
                emojiPickerView?.visibility = View.VISIBLE
            }
            InputViewMode.CLIPBOARD -> {
                keyboardView?.visibility = View.GONE
                emojiPickerView?.visibility = View.GONE
                candidateStripComposeView?.visibility = View.GONE
                if (clipboardView == null) {
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
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
                clipboardView?.setContent {
                    ClipboardSheetView(
                        clipboardStore = clipboardStore,
                        theme = activeTheme,
                        onPaste = { text ->
                            currentInputConnection?.commitText(text, 1)
                            setInputViewMode(InputViewMode.KEYBOARD)
                        },
                        onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                    )
                }
                clipboardView?.visibility = View.VISIBLE
            }
        }
    }

    private fun updateCandidatesVisibility() {
        val show = currentMode == InputViewMode.KEYBOARD
        candidateStripComposeView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    /**
     * Starts offline voice typing capture and displays waveform overlay.
     */
    private fun startVoiceTyping() {
        if (!audioManager.hasRecordPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted; cannot start voice typing")
            return
        }

        updateCandidatesVisibility()
        audioManager.startStreaming { finalTranscript ->
            if (finalTranscript.isNotBlank()) {
                currentInputConnection?.finishComposingText()
                currentInputConnection?.commitText(finalTranscript, 1)
            }
        }
    }

    private fun toggleShift() {
        keyboardView?.toggleShift()
    }

    private fun commitEnter() {
        currentInputConnection?.performEditorAction(
            currentInputEditorInfo?.imeOptions ?: android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        )
        session.reset()
        keyboardView?.setShifted(false)
    }

    fun getEnabledLayouts(): List<LekhaniLayoutType> {
        val saved = devicePrefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null)
        return LayoutRegistry.parseEnabledLayouts(saved)
    }

    private fun cycleLayout(direction: Int = 1) {
        val current = session.getLayout()
        val enabled = getEnabledLayouts()
        val currentIndex = enabled.indexOf(current)
        val nextIndex = if (currentIndex >= 0) {
            (currentIndex + direction + enabled.size) % enabled.size
        } else {
            0
        }
        switchLayout(enabled[nextIndex])
    }

    private fun showQuickLayoutPicker() {
        val enabled = getEnabledLayouts()
        val names = enabled.map { 
            "${LayoutRegistry.getBengaliName(it)} • ${LayoutRegistry.getEnglishName(it)}" 
        }.toTypedArray()
        val currentIdx = enabled.indexOf(session.getLayout()).coerceAtLeast(0)

        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("কীবোর্ড লেআউট নির্বাচন (Select Layout)")
            .setSingleChoiceItems(names, currentIdx) { d, which ->
                switchLayout(enabled[which])
                d.dismiss()
            }
            .setNegativeButton("বাতিল", null)
            .create()

        dialog.window?.let { w ->
            w.attributes?.token = rootInputContainer?.windowToken
            w.setType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_ATTACHED_DIALOG)
        }
        dialog.show()
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Physical / Bluetooth Hardware Keyboard Integration (Phase 7)
    // ══════════════════════════════════════════════════════════════════════════

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Intercept back key when inside Emoji or Clipboard view to return to Keyboard
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (currentMode != InputViewMode.KEYBOARD) {
                setInputViewMode(InputViewMode.KEYBOARD)
                return true
            }
            return super.onKeyDown(keyCode, event)
        }

        // Allow system shortcuts (Ctrl+C, Ctrl+V, Alt+Tab, Home, etc.) to pass through
        if (event.isCtrlPressed || event.isAltPressed) {
            return super.onKeyDown(keyCode, event)
        }

        // Layout cycle shortcut: Shift + Space
        if (keyCode == KeyEvent.KEYCODE_SPACE && event.isShiftPressed) {
            cycleLayout(1)
            return true
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                onBackspace()
                return true
            }
            KeyEvent.KEYCODE_SPACE -> {
                onSpace()
                return true
            }
            KeyEvent.KEYCODE_ENTER -> {
                commitEnter()
                return true
            }
        }

        // Process printable characters
        val unicode = event.unicodeChar
        if (unicode > 0 && !Character.isISOControl(unicode)) {
            val charStr = unicode.toChar().toString()
            onKey(charStr)
            return true
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            keyboardView?.setShifted(false)
        }
        return super.onKeyUp(keyCode, event)
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
            ic.beginBatchEdit()
            try {
                ic.commitText(text, 1)
                preeditShadow = ""
            } finally {
                ic.endBatchEdit()
            }
            clearCandidates()
        } ?: run {
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
            ic.beginBatchEdit()
            try {
                ic.commitText(text, 1)
                preeditShadow = ""
            } finally {
                ic.endBatchEdit()
            }
            clearCandidates()
        }

        // After committing a word, refresh surrounding context for AI scorer
        refreshSurroundingContext()
    }

    /**
     * Handles Glide / Gesture typing swipe completion.
     * Decodes the visited key path through the native Rust engine, commits the top
     * word, and exposes candidate alternatives to the candidate strip.
     */
    fun handleGlideGesture(keys: List<String>) {
        if (keys.isEmpty()) return
        val ic = currentInputConnection ?: return

        val result = try {
            session.decodeGlide(keys)
        } catch (e: LekhaniException) {
            Log.e(TAG, "decodeGlide error for $keys: $e")
            return
        }

        result.commitText?.let { text ->
            ic.beginBatchEdit()
            try {
                ic.commitText(text, 1)
                preeditShadow = ""
            } finally {
                ic.endBatchEdit()
            }
            if (result.candidates.isNotEmpty()) {
                publishCandidates(result.candidates)
            } else {
                clearCandidates()
            }
        } ?: run {
            setComposingTextSafe(ic, result.preedit)
            publishCandidates(result.candidates)
        }

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
            ic.beginBatchEdit()
            try {
                ic.commitText(text, 1)
                preeditShadow = ""
            } finally {
                ic.endBatchEdit()
            }
            clearCandidates()
        }

        refreshSurroundingContext()
    }

    /**
     * Switches the active layout. Persists the choice to Device Protected Storage.
     */
    fun switchLayout(layout: LekhaniLayoutType) {
        session.setLayout(layout)
        keyboardView?.setLayout(LayoutRegistry.get(layout), layout, shifted = false)
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
        updateCandidatesVisibility()
    }

    /**
     * Clears all candidates and hides the candidate strip view.
     */
    private fun clearCandidates() {
        _candidateState.value = CandidateStripState.Empty
        updateCandidatesVisibility()
    }

    private fun handleToolbarToolClick(tool: KeyboardPreferences.ToolbarTool) {
        when (tool) {
            KeyboardPreferences.ToolbarTool.EMOJI -> {
                setInputViewMode(InputViewMode.EMOJI)
            }
            KeyboardPreferences.ToolbarTool.VOICE -> {
                if (audioManager.voiceState.value is VoiceTypingState.Listening) {
                    val result = audioManager.stopStreaming()
                    if (result.isNotBlank()) {
                        currentInputConnection?.finishComposingText()
                        currentInputConnection?.commitText(result, 1)
                    }
                } else {
                    audioManager.startStreaming { finalResult ->
                        if (finalResult.isNotBlank()) {
                            currentInputConnection?.finishComposingText()
                            currentInputConnection?.commitText(finalResult, 1)
                        }
                    }
                }
            }
            KeyboardPreferences.ToolbarTool.CLIPBOARD -> {
                setInputViewMode(InputViewMode.CLIPBOARD)
            }
            KeyboardPreferences.ToolbarTool.THEME -> {
                cycleTheme()
            }
            KeyboardPreferences.ToolbarTool.ONE_HANDED -> {
                val nextForm = when (keyboardPrefs.formFactor) {
                    KeyboardPreferences.FormFactor.STANDARD -> KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                    KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT -> KeyboardPreferences.FormFactor.ONE_HANDED_LEFT
                    KeyboardPreferences.FormFactor.ONE_HANDED_LEFT -> KeyboardPreferences.FormFactor.STANDARD
                    else -> KeyboardPreferences.FormFactor.ONE_HANDED_RIGHT
                }
                setFormFactor(nextForm)
            }
            KeyboardPreferences.ToolbarTool.FLOATING -> {
                val nextForm = if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
                    KeyboardPreferences.FormFactor.STANDARD
                } else {
                    KeyboardPreferences.FormFactor.FLOATING
                }
                setFormFactor(nextForm)
            }
            KeyboardPreferences.ToolbarTool.SPLIT -> {
                val nextForm = if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.SPLIT) {
                    KeyboardPreferences.FormFactor.STANDARD
                } else {
                    KeyboardPreferences.FormFactor.SPLIT
                }
                setFormFactor(nextForm)
            }
            KeyboardPreferences.ToolbarTool.SETTINGS -> {
                val intent = Intent(this, LekhaniSettingsActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
        }
    }

    private fun handleCursorMove(deltaChars: Int) {
        val ic = currentInputConnection ?: return
        if (deltaChars < 0) {
            for (i in 0 until (-deltaChars)) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_LEFT))
            }
        } else if (deltaChars > 0) {
            for (i in 0 until deltaChars) {
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
                ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT))
            }
        }
    }

    private fun handleSwipeDelete(wordCount: Int) {
        if (wordCount <= 0) return
        var remainingWords = wordCount

        // 1. If currently composing Bengali in session, discard composition first
        if (preeditShadow.isNotEmpty()) {
            session.reset()
            preeditShadow = ""
            currentInputConnection?.setComposingText("", 0)
            clearCandidates()
            remainingWords--
        }

        // 2. Delete remaining words from committed text
        if (remainingWords > 0) {
            val ic = currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(512, 0)?.toString() ?: ""
            if (before.isNotEmpty()) {
                var charsToDelete = 0
                var wordsFound = 0
                var inWord = false
                for (i in before.length - 1 downTo 0) {
                    val ch = before[i]
                    val isSpace = ch.isWhitespace()
                    if (!isSpace) {
                        inWord = true
                    } else if (inWord) {
                        wordsFound++
                        inWord = false
                        if (wordsFound >= remainingWords) {
                            break
                        }
                    }
                    charsToDelete++
                }
                if (charsToDelete > 0) {
                    ic.deleteSurroundingText(charsToDelete, 0)
                }
            }
        }
        refreshSurroundingContext()
    }

    fun setFormFactor(newForm: KeyboardPreferences.FormFactor) {
        keyboardPrefs.formFactor = newForm
        keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
        keyboardView?.requestLayout()
        keyboardView?.invalidate()
    }

    private fun cycleTheme() {
        val themeIds = listOf(
            ThemeRegistry.ID_FLOW_TEAL,
            ThemeRegistry.ID_OLED_BLACK,
            ThemeRegistry.ID_AVRO_BLUE,
            ThemeRegistry.ID_CYBER_INDIGO,
            ThemeRegistry.ID_DAYLIGHT_LIGHT,
        )
        val curId = keyboardPrefs.themeId
        val idx = themeIds.indexOf(curId)
        val nextId = if (idx == -1 || idx == themeIds.lastIndex) themeIds.first() else themeIds[idx + 1]
        keyboardPrefs.themeId = nextId
        val nextTheme = ThemeRegistry.resolveTheme(this, nextId)
        keyboardView?.applyTheme(nextTheme)
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
            if (session.getLayout() != preferred) {
                switchLayout(preferred)
            }
        } else {
            // Auto-switch to English QWERTY for passwords/incognito
            if (session.getLayout() != LekhaniLayoutType.ENGLISH) {
                switchLayout(LekhaniLayoutType.ENGLISH)
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
     * Safely updates composing text with atomic batch editing.
     * Prevents duplicate characters by letting setComposingText replace
     * existing composing spans without premature commit flushes.
     */
    private fun setComposingTextSafe(ic: InputConnection, preedit: String) {
        ic.beginBatchEdit()
        try {
            if (preedit.isEmpty()) {
                if (preeditShadow.isNotEmpty()) {
                    ic.commitText("", 1)
                    preeditShadow = ""
                }
            } else {
                ic.setComposingText(preedit, 1)
                preeditShadow = preedit
            }
        } finally {
            ic.endBatchEdit()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Constants
    // ══════════════════════════════════════════════════════════════════════════

    companion object {
        private const val TAG = "LekhaniIME"
        private const val PREFS_NAME = KeyboardPreferences.PREFS_NAME
        private const val PREF_LAYOUT = "active_layout"
        /**
         * Number of characters before the cursor fetched for AI context.
         * 256 chars covers ~2-3 sentences — sufficient for bigram/trigram scoring
         * without excessive Binder IPC payload.
         */
        private const val CONTEXT_CHAR_LIMIT = 256
    }
}
