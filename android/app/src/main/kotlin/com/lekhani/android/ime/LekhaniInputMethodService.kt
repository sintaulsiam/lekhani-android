package com.lekhani.android.ime

import android.content.Context
import android.content.Intent
import java.io.File
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import android.provider.UserDictionary
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
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
import com.lekhani.android.data.avro.AvroReverseTransliterator
import com.lekhani.android.data.avro.AvroWordHistory
import com.lekhani.android.data.clipboard.LekhaniClipboardStore
import com.lekhani.android.data.dictionary.LekhaniAssetInstaller
import com.lekhani.android.data.emoji.EmojiData
import com.lekhani.android.data.emoji.EmojiRecentsManager
import com.lekhani.android.data.settings.KeyboardPreferences
import com.lekhani.android.data.smart.SmartAssistant
import com.lekhani.android.feedback.LekhaniFeedbackManager
import com.lekhani.android.ffi.AndroidLekhaniSession
import com.lekhani.android.ffi.LekhaniLayoutType
import com.lekhani.android.ffi.LekhaniException
import com.lekhani.android.model.Key
import com.lekhani.android.model.KeyAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import com.lekhani.android.model.LayoutRegistry
import com.lekhani.android.theme.ThemeRegistry
import com.lekhani.android.ui.LekhaniSettingsActivity
import com.lekhani.android.ui.candidate.CandidateBlacklist
import com.lekhani.android.ui.candidate.CandidateStripState
import com.lekhani.android.ui.candidate.CandidateStripView
import com.lekhani.android.ui.candidate.DeleteGranularity
import com.lekhani.android.ui.candidate.HomophoneAnnotator
import com.lekhani.android.ui.candidate.UndoInfo
import com.lekhani.android.ui.clipboard.ClipboardSheetView
import com.lekhani.android.ui.emoji.EmojiPickerView
import com.lekhani.android.ui.voice.VoiceWaveformOverlay
import com.lekhani.android.voice.AudioStreamingManager
import com.lekhani.android.voice.VoiceTypingState
import com.lekhani.android.model.NumberSymbolsLayout
import com.lekhani.android.ui.editor.TextEditorSheetView
import com.lekhani.android.ui.floating.FloatingHeaderView
import com.lekhani.android.ui.picker.QuickLayoutPickerSheet
import com.lekhani.android.ui.tools.ExtraToolsSheetView
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
 * The central Android IME service. Bridges the Kotlin/Android layer with the
 * Rust [AndroidLekhaniSession] engine via UniFFI-generated bindings.
 *
 * Architecture (Three-Tier):
 *   Tier 3 (this class) ──UniFFI──► Tier 2 (lekhani-android Rust crate)
 *                                        │
 *                                   Tier 1 (lekhani-core / lekhani-parser)
 *
 * System constraint compliance:
 *   - directBootAware="true"  — prefs use Device Protected Storage
 *   - Password field policy   — auto-switch to English, freeze learning
 *   - Landscape non-fullscreen — onEvaluateFullscreenMode() → false
 *   - WebView resilience       — pre-edit shadow buffer + cursor guard
 *   - Zero INTERNET permission — no network calls anywhere in this file
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
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    /** Dynamic theme StateFlow observed by CandidateStripView and auxiliary sheets. */
    private val _themeFlow by lazy {
        MutableStateFlow(ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId))
    }
    val themeFlow: StateFlow<com.lekhani.android.theme.KeyboardTheme> get() = _themeFlow.asStateFlow()

    /** Dynamic toolbar tools StateFlow observed by CandidateStripView. */
    private val _toolsFlow by lazy {
        MutableStateFlow(keyboardPrefs.getActiveToolbarTools())
    }
    val toolsFlow: StateFlow<List<KeyboardPreferences.ToolbarTool>> get() = _toolsFlow.asStateFlow()

    /** Dynamic input view mode StateFlow observed by CandidateStripView. */
    private val _inputViewModeFlow by lazy {
        MutableStateFlow(InputViewMode.KEYBOARD)
    }

    /** Persists long-press blacklisted candidates to Device Protected Storage. */
    private val blacklist: CandidateBlacklist by lazy { CandidateBlacklist(this) }

    /** Audio streaming manager for 100% offline voice typing (Phase 5). */
    private val audioManager: AudioStreamingManager by lazy { AudioStreamingManager(this, coroutineScope = serviceScope) }

    // ── Input modes & auxiliary views (Phase 6) ──────────────────────────────

    enum class InputViewMode { KEYBOARD, EMOJI, EMOJI_SEARCH, CLIPBOARD, TEXT_EDITOR, RESIZE, TOOLS_MENU }

    private var currentMode: InputViewMode = InputViewMode.KEYBOARD
    private var emojiSearchQuery: String = ""
    private var emojiSearchRawQuery: String = ""
    private var emojiSearchSession: AndroidLekhaniSession? = null
    private var rootInputContainer: ViewGroup? = null
    private var floatingCardContainer: LinearLayout? = null
    private var floatingHeaderComposeView: ComposeView? = null
    private var floatingFooterComposeView: ComposeView? = null
    private var modesContainer: FrameLayout? = null
    private var candidateStripComposeView: ComposeView? = null
    private var resizeOverlayComposeView: ComposeView? = null
    private var emojiPickerView: ComposeView? = null
    private var clipboardView: ComposeView? = null
    private var textEditorView: ComposeView? = null
    private var toolsMenuView: ComposeView? = null
    private var quickLayoutPickerComposeView: ComposeView? = null
    private val _showQuickLayoutPickerFlow = MutableStateFlow(false)
    private val _paletteHeightDp = MutableStateFlow(304.dp)
    private var isCurrentFieldPrivate: Boolean = false
    private var consumedBackOnKeyDown: Boolean = false
    private var isEnglishDictLoaded: Boolean = false
    private var previousLayoutBeforePassword: LekhaniLayoutType? = null
    private var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    private var layoutPrefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastMeasuredKeyboardHeightPx: Int = 0
    private var lastMeasuredStripHeightPx: Int = 0
    private var lastTouchCoordinates: Pair<Float, Float>? = null

    private var isNumericMode: Boolean = false
    private var isMoreSymbolsMode: Boolean = false
    private var isBengaliDigitsMode: Boolean = false
    private var isNumericFieldMode: Boolean = false
    private var isPhoneDialpadMode: Boolean = false
    private var previousLayoutBeforeNumeric: LekhaniLayoutType? = null

    private val recentsManager: EmojiRecentsManager by lazy { EmojiRecentsManager(this) }
    private val clipboardStore: LekhaniClipboardStore by lazy { LekhaniClipboardStore(this) }
    private var lastCopiedText: String? = null
    private var lastCopiedTime: Long = 0L
    private var isQuickChipDismissed: Boolean = false
    private var lastAutoDariCommitTime: Long = 0L

    // ── WebView / Chromium composing shadow buffer ─────────────────────────────

    /**
     * Shadow copy of the last committed preedit text sent to setComposingText().
     * Required for WebView / Chromium resilience: Chromium occasionally drops
     * composing state silently. Before committing, we verify the shadow matches
     * what the InputConnection actually reports and re-set if diverged.
     */
    private var preeditShadow: String = ""
    private var currentSelStart: Int = -1
    private var currentSelEnd: Int = -1
    private var editorSelectionAnchor: Int = -1
    private var isSwipeDeleteActive: Boolean = false
    private var swipeDeleteAnchorCursor: Int = -1
    private var activeSwipeDeletePreviewText: String = ""
    @Volatile private var activeSwipeSnapshotText: String = ""

    // Raw keystroke buffer and undo action state
    private val rawInputBuffer = StringBuilder()
    private val avroHistory = AvroWordHistory(maxEntries = 100)
    private var activeInspectedWord: InspectedWord? = null
    private val wordInspectionSession: AndroidLekhaniSession by lazy {
        AndroidLekhaniSession().apply {
            setLayout(LekhaniLayoutType.AVRO)
        }
    }
    private var activeUndoInfo: UndoInfo? = null
    private var undoDismissJob: kotlinx.coroutines.Job? = null
    private var refreshContextJob: kotlinx.coroutines.Job? = null
    private var lastSpaceTapTime: Long = 0L
    private var lastPredictedContext: String = ""
    private var lastCommitWithTrailingSpaceTime: Long = 0L

    /**
     * Set to `true` immediately after a word is committed (space, candidate tap, backspace-undo).
     * Prevents [refreshSurroundingContext] from flashing next-word predictions onto the strip
     * before the user begins typing the next word. Cleared on the first keystroke of a new word.
     */
    @Volatile private var suppressNextWordAfterCommit: Boolean = false

    /**
     * Last cached surrounding text for use in word-count scan during swipe-to-delete.
     * Updated asynchronously by [refreshSurroundingContext] — avoids blocking the main thread
     * with a synchronous `getTextBeforeCursor()` Binder IPC call in [handleSwipeDelete].
     */
    @Volatile private var cachedSurroundingContext: String = ""

    // Pre-allocated KeyEvent instances for cursor movement — zero allocation in handleCursorMove
    private val curLeftDown  by lazy { android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_LEFT) }
    private val curLeftUp    by lazy { android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_DPAD_LEFT) }
    private val curRightDown by lazy { android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
    private val curRightUp   by lazy { android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,   android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }

    // ── Preferences (Device Protected Storage) ─────────────────────────────────

    /**
     * Returns a SharedPreferences backed by Device Protected Storage.
     *
     * When directBootAware="true", preferences MUST use DPS
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

    // Lifecycle

    override fun onCreate() {
        super.onCreate()
        imeLifecycleOwner.onCreate()
        imeLifecycleOwner.onResume()

        // Unpack bundled offline dictionaries and load models asynchronously off the main thread
        serviceScope.launch(Dispatchers.IO) {
            try {
                LekhaniAssetInstaller.installAssetsIfNeeded(applicationContext)
                val dictDir = File(filesDir, "dictionaries")
                if (dictDir.exists()) {
                    try {
                        com.lekhani.android.ffi.setDictionaryDirectory(dictDir.absolutePath)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to set custom dictionary dir: ${e.message}")
                    }
                }
                ensureEnglishDictionaryLoaded()
            } catch (e: Exception) {
                Log.e(TAG, "Error installing offline assets: ${e.message}")
            }
            try {
                val f = File(filesDir, "user_learned.bin")
                session.setLearnerAutosavePath(f.absolutePath)
                if (f.exists()) {
                    val ok = session.loadUserLearned(f.absolutePath)
                    Log.i(TAG, "User learned dictionary loaded ($ok): ${f.absolutePath}")
                }
                val sysAcFile = File(filesDir, "dictionaries/autocorrect.json")
                if (sysAcFile.exists()) {
                    val ok = session.loadUserAutocorrect(sysAcFile.absolutePath)
                    Log.i(TAG, "Bundled system autocorrect rules loaded ($ok): ${sysAcFile.absolutePath}")
                }
                val acFile = File(filesDir, "user_autocorrect.json")
                if (acFile.exists()) {
                    val ok = session.loadUserAutocorrect(acFile.absolutePath)
                    Log.i(TAG, "User autocorrect rules loaded ($ok): ${acFile.absolutePath}")
                }
                importSystemUserDictionaryIfNeeded()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading user learned dictionary or autocorrect: ${e.message}")
            }
        }

        // Restore the user's last-used layout from Device Protected Storage.
        val savedLayout = getSavedLayout()
        session.setLayout(savedLayout)
        Log.i(TAG, "Lekhani IME created; layout = $savedLayout")

        // Listen for system clipboard updates; guard against password field capture
        val sysClipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            syncSystemClipboard()
        }
        clipboardListener = listener
        sysClipboard?.addPrimaryClipChangedListener(listener)

        // Listen for layout changes from Settings (e.g. LayoutFlowScreen card tap)
        val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == PREF_LAYOUT) {
                val newName = sp.getString(key, null)
                val newLayout = newName?.let { runCatching { LekhaniLayoutType.valueOf(it) }.getOrNull() }
                if (newLayout != null && session.getLayout() != newLayout) {
                    switchLayout(newLayout)
                }
            }
        }
        layoutPrefListener = prefListener
        devicePrefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /**
     * Synchronizes the system primary clipboard into Lekhani's local store.
     * Guaranteed to work in Android 10+ where background listeners may be restricted.
     */
    private fun syncSystemClipboard() {
        val sysClipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
        runCatching {
            if (!isCurrentFieldPrivate && sysClipboard.hasPrimaryClip()) {
                val clip = sysClipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val item = clip.getItemAt(0)
                    val text = item?.text?.toString() ?: item?.coerceToText(this@LekhaniInputMethodService)?.toString()
                    if (LekhaniClipboardStore.isValidClipText(text)) {
                        val trimmed = text!!.trim()
                        if (trimmed != lastCopiedText) {
                            lastCopiedText = trimmed
                            lastCopiedTime = System.currentTimeMillis()
                            isQuickChipDismissed = false
                        }
                        clipboardStore.addClip(trimmed)
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to sync system primary clip: ${e.message}")
        }
    }

    private fun checkAndShowQuickChip() {
        if (isCurrentFieldPrivate || isQuickChipDismissed) return
        if (session.isComposing() || preeditShadow.isNotEmpty()) return

        val text = lastCopiedText ?: return
        if (!LekhaniClipboardStore.isValidClipText(text)) return
        val ageMs = System.currentTimeMillis() - lastCopiedTime
        if (ageMs in 0..120_000) {
            val isEng = session.getLayout() == LekhaniLayoutType.ENGLISH
            val chipInfo = SmartAssistant.inspectClipboard(text, isEng)
            if (chipInfo != null) {
                _candidateState.value = CandidateStripState.QuickChip(
                    label = chipInfo.label,
                    icon = chipInfo.icon,
                    pasteText = chipInfo.fullText,
                    chipType = chipInfo.type,
                )
                updateCandidatesVisibility()
            }
        }
    }

    override fun onDestroy() {
        clipboardListener?.let { listener ->
            val sysClipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            sysClipboard?.removePrimaryClipChangedListener(listener)
            clipboardListener = null
        }
        layoutPrefListener?.let { listener ->
            devicePrefs.unregisterOnSharedPreferenceChangeListener(listener)
            layoutPrefListener = null
        }
        // Launch persist on a separate IO scope so it finishes even as service tears down
        val file = File(filesDir, "user_learned.bin")
        CoroutineScope(Dispatchers.IO).launch {
            persistUserLearnedInternal(file)
        }
        imeLifecycleOwner.onDestroy()
        audioManager.release()
        feedbackManager.release()
        clipboardView = null
        emojiPickerView = null
        textEditorView = null
        toolsMenuView = null
        resizeOverlayComposeView = null
        modesContainer = null
        keyboardView = null
        candidateStripComposeView = null
        floatingCardContainer = null
        floatingHeaderComposeView = null
        floatingFooterComposeView = null
        rootInputContainer = null
        serviceScope.cancel()
        try {
            wordInspectionSession.destroy()
        } catch (_: Exception) {}
        super.onDestroy()
    }

    // Input session lifecycle  (called on every editor focus/blur)

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        clearUndo()
        rawInputBuffer.clear()
        session.reset()
        preeditShadow = ""
        cachedSurroundingContext = ""
        activeSwipeSnapshotText = ""
        isSwipeDeleteActive = false
        swipeDeleteAnchorCursor = -1
        activeSwipeDeletePreviewText = ""
        editorSelectionAnchor = -1
        suppressNextWordAfterCommit = false
        setInputViewMode(InputViewMode.KEYBOARD)
        applyInputTypePolicy(info)
        updateEnterActionAndFieldType(info)
        refreshSurroundingContext()
    }

    override fun onFinishInput() {
        clearUndo()
        rawInputBuffer.clear()
        audioManager.cancelStreaming()
        session.reset()
        preeditShadow = ""
        cachedSurroundingContext = ""
        activeSwipeSnapshotText = ""
        isSwipeDeleteActive = false
        swipeDeleteAnchorCursor = -1
        activeSwipeDeletePreviewText = ""
        previousLayoutBeforeNumeric = null
        previousLayoutBeforePassword = null
        avroHistory.clear()
        clearCandidates()
        persistUserLearnedAsync()
        super.onFinishInput()
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        ensureEnglishDictionaryLoaded()
        clipboardStore.retentionMinutes = keyboardPrefs.clipboardRetentionMinutes
        clipboardStore.pruneExpiredClips()
        syncSystemClipboard()
        currentSelStart = -1
        currentSelEnd = -1
        // Re-apply policy in case the editor info changed after the view appeared
        applyInputTypePolicy(info)
        updateEnterActionAndFieldType(info)
        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)
        _themeFlow.value = activeTheme
        keyboardView?.applyTheme(activeTheme)
        keyboardView?.setGboardKarsActive(false)
        keyboardView?.enabledLayoutsCount = getEnabledLayouts().size
        // Self-healing synchronization: Ensure keyboardView's layout matches session layout
        val curLayout = session.getLayout()
        if (!isNumericMode && !isNumericFieldMode && !isPhoneDialpadMode) {
            if (keyboardView?.currentLayoutType != curLayout) {
                keyboardView?.setLayout(LayoutRegistry.get(curLayout), curLayout, shifted = false)
            }
        }
        feedbackManager.updateCache()
        keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
        applyFloatingCardLayout(keyboardPrefs.formFactor)
        rootInputContainer?.post {
            updateParentLayoutHierarchy(keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING)
        }
        session.setAutoLearnEnabled(keyboardPrefs.autoLearnWordsEnabled)
        updateCandidatesVisibility()
        checkAndShowQuickChip()
        updateAutoCaps()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // 1. Immediately terminate hardware microphone recording and speech streams.
        // Leaving an active AudioRecord stream while the IME window is hidden causes
        // Android 11+ background privacy monitors and OEM battery keepers to kill the process.
        audioManager.cancelStreaming()

        // 2. Drop transient auxiliary engine sessions and candidate strip arrays.
        emojiSearchSession = null
        _candidateState.value = CandidateStripState.Empty
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            // High memory pressure and IME is backgrounded.
            // Drop heavy transient objects and let the runtime reclaim memory naturally.
            audioManager.cancelStreaming()
            emojiSearchSession = null
            _candidateState.value = CandidateStripState.Empty
        }
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
        currentSelStart = newSelStart
        currentSelEnd = newSelEnd
        if (newSelStart == 0 && newSelEnd == 0) {
            cachedSurroundingContext = ""
        }

        // Check if user manually repositioned the cursor or changed selection
        if (session.isComposing() || preeditShadow.isNotEmpty()) {
            val hasComposingSpan = candidatesStart >= 0 && candidatesEnd >= 0
            val cursorOutsideSpan = hasComposingSpan && (newSelStart < candidatesStart || newSelEnd > candidatesEnd)
            val cursorMovedInsideSpan = hasComposingSpan && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)
            val hasSelectionRange = newSelStart != newSelEnd && newSelStart >= 0 && newSelEnd >= 0
            val cursorMovedWithoutSpan = !hasComposingSpan && (newSelStart != oldSelStart)

            if (cursorOutsideSpan || cursorMovedInsideSpan || hasSelectionRange || cursorMovedWithoutSpan) {
                // User manually tapped away from the composing region or highlighted text.
                // Finalize active composing text so it stays in place as committed text.
                currentInputConnection?.finishComposingText()
                session.reset()
                preeditShadow = ""
                rawInputBuffer.clear()
                activeInspectedWord = null
                clearUndo()
                refreshSurroundingContext()
                val isAvro = session.getLayout() == LekhaniLayoutType.AVRO && !isCurrentFieldPrivate
                if (!isAvro) {
                    clearCandidates()
                } else {
                    currentInputConnection?.let { updateAvroStripForWordAtCursor(it) }
                }
            }
        } else if (newSelStart == newSelEnd && newSelStart >= 0) {
            // User tapped somewhere in text to reposition the cursor while idle.
            // Respect the user's intended cursor position without hijacking or resetting it.
            // Clear the post-commit suppression so word inspection (e.g. "Sonar" preview)
            // shows correctly when the user taps back into a committed word.
            suppressNextWordAfterCommit = false
            refreshSurroundingContext()
            updateAutoCaps()
            currentInputConnection?.let { updateAvroStripForWordAtCursor(it) }
        }

        // Contextual Text Selection Toolbar shown whenever text is highlighted
        val hasSelection = (newSelStart != newSelEnd && newSelStart >= 0 && newSelEnd >= 0)
        if (hasSelection) {
            // Guard: Do not clobber active SwipeDeletePreview
            if (_candidateState.value is CandidateStripState.SwipeDeletePreview) {
                return
            }
            // If user highlights text, dismiss any pending Undo chip
            if (_candidateState.value is CandidateStripState.Undo) {
                clearUndo()
            }
            val ic = currentInputConnection
            _candidateState.value = CandidateStripState.Selection(
                onCut = {
                    ic?.performContextMenuAction(android.R.id.cut)
                    _candidateState.value = CandidateStripState.Empty
                    updateCandidatesVisibility()
                    refreshSurroundingContext()
                },
                onCopy = {
                    ic?.performContextMenuAction(android.R.id.copy)
                    // Standard UX: collapse selection to deselect and return to normal candidate bar
                    val collapsePos = maxOf(newSelStart, newSelEnd)
                    if (collapsePos >= 0) {
                        ic?.setSelection(collapsePos, collapsePos)
                    }
                    _candidateState.value = CandidateStripState.Empty
                    updateCandidatesVisibility()
                    refreshSurroundingContext()
                },
                onPaste = {
                    ic?.performContextMenuAction(android.R.id.paste)
                    _candidateState.value = CandidateStripState.Empty
                    updateCandidatesVisibility()
                    refreshSurroundingContext()
                },
                onSelectAll = {
                    if (ic?.performContextMenuAction(android.R.id.selectAll) != true) {
                        sendEditorKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
                    }
                },
                onDelete = {
                    ic?.commitText("", 0)
                    _candidateState.value = CandidateStripState.Empty
                    updateCandidatesVisibility()
                    refreshSurroundingContext()
                },
                onDeselect = {
                    val collapsePos = maxOf(newSelStart, newSelEnd)
                    if (collapsePos >= 0) {
                        ic?.setSelection(collapsePos, collapsePos)
                    }
                    _candidateState.value = CandidateStripState.Empty
                    updateCandidatesVisibility()
                    refreshSurroundingContext()
                }
            )
            candidateStripComposeView?.visibility = View.VISIBLE
        } else if (_candidateState.value is CandidateStripState.Selection) {
            _candidateState.value = CandidateStripState.Empty
            updateCandidatesVisibility()
            refreshSurroundingContext()
        }
    }

    // UI — keyboard view (stub; KeyboardCanvasView implemented in Phase 3)

    override fun onConfigureWindow(win: Window, isFullscreen: Boolean, isCandidatesOnly: Boolean) {
        super.onConfigureWindow(win, isFullscreen, isCandidatesOnly)
        attachLifecycleOwner(win.decorView)
        if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setGravity(Gravity.TOP or Gravity.START)
            win.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        } else {
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            win.setGravity(Gravity.BOTTOM)
        }
        rootInputContainer?.post {
            updateParentLayoutHierarchy(keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
            applyFloatingCardLayout(keyboardPrefs.formFactor)
        }
    }

    private fun attachLifecycleOwner(view: View) {
        view.setViewTreeLifecycleOwner(imeLifecycleOwner)
        view.setViewTreeViewModelStoreOwner(imeLifecycleOwner)
        view.setViewTreeSavedStateRegistryOwner(imeLifecycleOwner)
    }

    override fun onCreateInputView(): View {
        window?.window?.decorView?.let { attachLifecycleOwner(it) }
        clipboardView = null
        emojiPickerView = null
        textEditorView = null
        toolsMenuView = null
        resizeOverlayComposeView = null

        val rootLayout = object : FrameLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
                    val dm = resources.displayMetrics
                    val fullWSpec = MeasureSpec.makeMeasureSpec(dm.widthPixels, MeasureSpec.EXACTLY)
                    val fullHSpec = MeasureSpec.makeMeasureSpec(dm.heightPixels, MeasureSpec.EXACTLY)
                    super.onMeasure(fullWSpec, fullHSpec)
                    setMeasuredDimension(dm.widthPixels, dm.heightPixels)
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
        }.apply {
            clipChildren = false
            clipToPadding = false
            attachLifecycleOwner(this)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
                    FrameLayout.LayoutParams.MATCH_PARENT
                } else {
                    FrameLayout.LayoutParams.WRAP_CONTENT
                }
            )
        }
        rootInputContainer = rootLayout

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            attachLifecycleOwner(this)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        cardLayout.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
                val displayHeight = resources.displayMetrics.heightPixels
                val density = resources.displayMetrics.density
                val floatingH = cardLayout.height
                if (floatingH > 0) {
                    val minY = 48f * density
                    val maxY = (displayHeight - floatingH - 24f * density).coerceAtLeast(minY)
                    if (cardLayout.translationY > maxY) {
                        cardLayout.translationY = maxY
                    }
                }
                requestInsetsRecalculation()
            }
        }
        floatingCardContainer = cardLayout
        rootLayout.addView(cardLayout)

        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)

        val floatingHeader = ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentTheme by _themeFlow.collectAsState()
                FloatingHeaderView(
                    theme = currentTheme,
                    isEnglish = (keyboardPrefs.uiLanguage == "en"),
                    onDragDelta = { dx, dy ->
                        handleFloatingDrag(dx, dy)
                    },
                    onDragEnd = {
                        saveFloatingPosition()
                    },
                    onDockToStandard = {
                        setFormFactor(KeyboardPreferences.FormFactor.STANDARD)
                    }
                )
            }
            visibility = if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) View.VISIBLE else View.GONE
        }
        floatingHeaderComposeView = floatingHeader
        cardLayout.addView(
            floatingHeader,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val candidateStrip = ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentTheme by _themeFlow.collectAsState()
                val currentTools by _toolsFlow.collectAsState()
                val currentMode by _inputViewModeFlow.collectAsState()
                Box {
                    CandidateStripView(
                        stateFlow = candidateState,
                        onCandidateClick = { text ->
                            candidateStripComposeView?.let { feedbackManager.onKeyFeedback(it) }
                            onCandidateSelected(text)
                        },
                        onBlacklist = { text ->
                            blacklist.add(text)
                            try {
                                session.deleteUserWord(text)
                                persistUserLearnedAsync()
                                val msg = if (keyboardPrefs.uiLanguage == "en") "Removed from suggestions" else "পরামর্শটি মুছে ফেলা হয়েছে"
                                android.widget.Toast.makeText(this@LekhaniInputMethodService, msg, android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to delete user word '$text': ${e.message}")
                            }
                            // Re-publish current candidates with the blacklisted word removed
                            val current = _candidateState.value
                            if (current is CandidateStripState.Candidates) {
                                val filtered = current.items.filter { it.text != text }
                                _candidateState.value = if (filtered.isEmpty())
                                    CandidateStripState.Empty
                                else
                                    CandidateStripState.Candidates(filtered, undoInfo = current.undoInfo)
                            }
                        },
                        theme = currentTheme,
                        activeTools = currentTools,
                        isEnglish = (keyboardPrefs.uiLanguage == "en"),
                        isToolsMenuOpen = (currentMode == InputViewMode.TOOLS_MENU),
                        onToolClick = { tool ->
                            candidateStripComposeView?.let { feedbackManager.onKeyFeedback(it) }
                            handleToolbarToolClick(tool)
                        },
                        onOpenToolsMenu = {
                            if (currentMode == InputViewMode.TOOLS_MENU) {
                                setInputViewMode(InputViewMode.KEYBOARD)
                            } else {
                                setInputViewMode(InputViewMode.TOOLS_MENU)
                            }
                        },
                        onEmojiSearchClose = {
                            setInputViewMode(InputViewMode.EMOJI)
                        },
                        onEmojiSearchClear = {
                            emojiSearchQuery = ""
                            emojiSearchRawQuery = ""
                            emojiSearchSession?.reset()
                            updateEmojiSearchStrip()
                        },
                        onEmojiSearchExitToKeyboard = {
                            setInputViewMode(InputViewMode.KEYBOARD)
                        },
                        onUndoClick = { undo ->
                            candidateStripComposeView?.let { feedbackManager.onKeyFeedback(it) }
                            onUndoCommit(undo)
                        },
                    )

                    VoiceWaveformOverlay(
                        voiceStateFlow = audioManager.voiceState,
                        isEnglish = keyboardPrefs.uiLanguage == "en",
                        onDone = {
                            val result = audioManager.stopStreaming()
                            if (result.isNotBlank()) {
                                currentInputConnection?.finishComposingText()
                                currentInputConnection?.commitText(result, 1)
                                // Forward the voice transcript to the AI context window so the
                                // N-gram / neural next-word predictor can use it for the next
                                // typed word, exactly as keyboard commits do.
                                refreshSurroundingContext()
                            }
                        },
                        onCancel = {
                            audioManager.cancelStreaming()
                        },
                    )
                }
            }
        }
        candidateStrip.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (candidateStrip.height > 0) {
                lastMeasuredStripHeightPx = candidateStrip.height
            }
        }
        candidateStripComposeView = candidateStrip
        candidateStrip.visibility = View.VISIBLE
        cardLayout.addView(
            candidateStrip,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val resizeOverlay = ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
        }
        resizeOverlayComposeView = resizeOverlay
        cardLayout.addView(
            resizeOverlay,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val container = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
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

                override fun onKeyWithTouch(key: Key, action: KeyAction, touchX: Float, touchY: Float) {
                    if (action is KeyAction.Character) {
                        lastTouchCoordinates = Pair(touchX, touchY)
                    }
                }

                override fun onGeometryChanged(geometries: List<com.lekhani.android.ffi.KeyGeometryConfig>) {
                    session.updateKeyboardGeometry(geometries)
                }

                override fun onSpaceSwipe(direction: Int) {
                    cycleLayout(direction)
                }

                override fun onSpaceSwipeUp() {
                    setInputViewMode(InputViewMode.TEXT_EDITOR)
                }

                override fun onSpaceLongPress() {
                    if (session.getLayout() == LekhaniLayoutType.AVRO && session.isComposing()) {
                        commitForceAvro()
                    } else {
                        showQuickLayoutPicker()
                    }
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
                    handleSwipeDeletePreview(wordCount)
                }

                override fun canSwipeDelete(): Boolean {
                    if (session.isComposing() || preeditShadow.isNotEmpty() || rawInputBuffer.isNotEmpty()) {
                        return true
                    }
                    val ic = currentInputConnection ?: return false
                    return try {
                        ic.getTextBeforeCursor(1, 0)?.isNotEmpty() == true
                    } catch (_: Exception) {
                        false
                    }
                }

                override fun onFormFactorChange(newFormFactor: KeyboardPreferences.FormFactor) {
                    setFormFactor(newFormFactor)
                }
            }
        }
        canvasView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (canvasView.height > 0) {
                lastMeasuredKeyboardHeightPx = canvasView.height
            }
        }
        keyboardView = canvasView
        container.clipChildren = false
        container.clipToPadding = false
        container.addView(
            canvasView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        // Pre-warm EmojiPickerView in background to eliminate first-tap cold inflation hitch
        val prewarmedEmojiPicker = ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            visibility = View.GONE
            setContent {
                val theme by _themeFlow.collectAsState()
                val heightDp by _paletteHeightDp.collectAsState()
                EmojiPickerView(
                    recentsManager = recentsManager,
                    theme = theme,
                    isEnglish = (keyboardPrefs.uiLanguage == "en"),
                    paletteHeight = heightDp,
                    onEmojiSelected = { emoji ->
                        emojiPickerView?.let { feedbackManager.onKeyFeedback(it) }
                        currentInputConnection?.commitText(emoji, 1)
                    },
                    onBackspace = { onBackspace() },
                    onSpace = { onSpace() },
                    onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                    onSearchClick = { query ->
                        setInputViewMode(InputViewMode.EMOJI_SEARCH, query)
                    },
                )
            }
        }
        emojiPickerView = prewarmedEmojiPicker
        container.addView(
            prewarmedEmojiPicker,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )
        cardLayout.clipChildren = false
        cardLayout.clipToPadding = false
        cardLayout.addView(
            container,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val floatingFooter = ComposeView(this).apply {
            attachLifecycleOwner(this)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val currentTheme by _themeFlow.collectAsState()
                com.lekhani.android.ui.floating.FloatingBottomDragBar(
                    theme = currentTheme,
                    onDragDelta = { dx, dy ->
                        handleFloatingDrag(dx, dy)
                    },
                    onDragEnd = {
                        saveFloatingPosition()
                    }
                )
            }
            visibility = if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) View.VISIBLE else View.GONE
        }
        floatingFooterComposeView = floatingFooter
        cardLayout.addView(
            floatingFooter,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        applyFloatingCardLayout(keyboardPrefs.formFactor)
        return rootLayout
    }

    override fun onCreateCandidatesView(): View? = null

    // ── Internal key dispatch ─────────────────────────────────────────────────

    private fun handleKeyAction(key: Key, action: KeyAction) {
        if (_candidateState.value is CandidateStripState.Selection) {
            _candidateState.value = CandidateStripState.Empty
            updateCandidatesVisibility()
        }
        if (currentMode == InputViewMode.EMOJI_SEARCH) {
            when (action) {
                is KeyAction.Character -> {
                    emojiSearchRawQuery += action.token
                    val sSession = emojiSearchSession ?: AndroidLekhaniSession().apply {
                        setLayout(session.getLayout())
                        emojiSearchSession = this
                    }
                    val res = try {
                        sSession.processKey(action.token)
                    } catch (e: Exception) {
                        null
                    }
                    if (res != null) {
                        if (res.preedit.isNotEmpty()) {
                            emojiSearchQuery = res.preedit
                        } else if (!res.commitText.isNullOrEmpty()) {
                            emojiSearchQuery += res.commitText
                        } else {
                            emojiSearchQuery += action.token
                        }
                    } else {
                        emojiSearchQuery += action.token
                    }
                    updateEmojiSearchStrip()
                    return
                }
                KeyAction.Backspace -> {
                    val sSession = emojiSearchSession
                    if (sSession != null && sSession.isComposing()) {
                        val res = try {
                            sSession.handleBackspace()
                        } catch (e: Exception) {
                            null
                        }
                        emojiSearchQuery = res?.preedit ?: ""
                        if (emojiSearchRawQuery.isNotEmpty()) {
                            emojiSearchRawQuery = emojiSearchRawQuery.dropLast(1)
                        }
                        updateEmojiSearchStrip()
                    } else if (emojiSearchQuery.isNotEmpty() || emojiSearchRawQuery.isNotEmpty()) {
                        if (emojiSearchQuery.isNotEmpty()) {
                            emojiSearchQuery = emojiSearchQuery.dropLast(1)
                        }
                        if (emojiSearchRawQuery.isNotEmpty()) {
                            emojiSearchRawQuery = emojiSearchRawQuery.dropLast(1)
                        }
                        updateEmojiSearchStrip()
                    } else {
                        setInputViewMode(InputViewMode.EMOJI)
                    }
                    return
                }
                KeyAction.Space -> {
                    val sSession = emojiSearchSession
                    if (sSession != null && sSession.isComposing()) {
                        val res = try { sSession.handleSpace() } catch (e: Exception) { null }
                        if (res != null && !res.commitText.isNullOrEmpty()) {
                            emojiSearchQuery = res.commitText + " "
                        } else {
                            emojiSearchQuery += " "
                        }
                    } else {
                        emojiSearchQuery += " "
                    }
                    emojiSearchRawQuery += " "
                    updateEmojiSearchStrip()
                    return
                }
                KeyAction.Enter -> {
                    val cur = _candidateState.value
                    if (cur is CandidateStripState.EmojiSearch && cur.emojis.isNotEmpty()) {
                        onCandidateSelected(cur.emojis.first())
                    } else {
                        setInputViewMode(InputViewMode.KEYBOARD)
                    }
                    return
                }
                KeyAction.Shift -> {
                    toggleShift()
                    return
                }
                KeyAction.SwitchNumeric -> {
                    setInputViewMode(InputViewMode.KEYBOARD)
                    isNumericMode = true
                    isMoreSymbolsMode = false
                    updateNumberSymbolsKeyboard()
                    return
                }
                KeyAction.SwitchMoreSymbols -> {
                    setInputViewMode(InputViewMode.KEYBOARD)
                    isNumericMode = true
                    isMoreSymbolsMode = true
                    updateNumberSymbolsKeyboard()
                    return
                }
                KeyAction.SwitchAlpha -> {
                    setInputViewMode(InputViewMode.KEYBOARD)
                    restoreAlphaKeyboard()
                    return
                }
                KeyAction.ToggleBengaliDigits -> {
                    isBengaliDigitsMode = !isBengaliDigitsMode
                    updateNumberSymbolsKeyboard()
                    return
                }
                KeyAction.SwitchLayout -> {
                    cycleLayout()
                    emojiSearchSession?.setLayout(session.getLayout())
                    return
                }
                KeyAction.VoiceTyping -> {
                    startVoiceTyping()
                    return
                }
                KeyAction.SwitchEmoji -> {
                    setInputViewMode(InputViewMode.EMOJI)
                    return
                }
                KeyAction.SwitchClipboard -> {
                    setInputViewMode(InputViewMode.CLIPBOARD)
                    return
                }
                KeyAction.SwitchNumpad -> {
                    setInputViewMode(InputViewMode.KEYBOARD)
                    if (previousLayoutBeforeNumeric == null) {
                        previousLayoutBeforeNumeric = session.getLayout()
                    }
                    isNumericFieldMode = true
                    isPhoneDialpadMode = false
                    isNumericMode = true
                    isMoreSymbolsMode = false
                    updateNumberSymbolsKeyboard()
                    return
                }
                KeyAction.CursorLeft, KeyAction.CursorRight, KeyAction.Tab, KeyAction.ToggleGboardVowels -> return
            }
        }

        when (action) {
            is KeyAction.Character -> handleCharacterInput(key, action.token)
            KeyAction.Backspace    -> onBackspace()
            KeyAction.Space        -> onSpace()
            KeyAction.Enter        -> commitEnter()
            KeyAction.Shift        -> toggleShift()
            KeyAction.SwitchNumpad -> {
                if (isNumericFieldMode || isPhoneDialpadMode) {
                    restoreAlphaKeyboard()
                } else {
                    if (previousLayoutBeforeNumeric == null) {
                        previousLayoutBeforeNumeric = session.getLayout()
                    }
                    isNumericFieldMode = true
                    isPhoneDialpadMode = false
                    isNumericMode = true
                    isMoreSymbolsMode = false
                    setInputViewMode(InputViewMode.KEYBOARD)
                    updateNumberSymbolsKeyboard()
                }
            }
            KeyAction.SwitchNumeric -> {
                if (isNumericFieldMode || isPhoneDialpadMode) {
                    isNumericFieldMode = false
                    isPhoneDialpadMode = false
                    isNumericMode = true
                    isMoreSymbolsMode = false
                    updateNumberSymbolsKeyboard()
                    return
                }
                if (!isNumericMode) {
                    isNumericMode = true
                    isMoreSymbolsMode = false
                } else if (isMoreSymbolsMode) {
                    isMoreSymbolsMode = false
                } else {
                    restoreAlphaKeyboard()
                    return
                }
                updateNumberSymbolsKeyboard()
            }
            KeyAction.SwitchMoreSymbols -> {
                isNumericMode = true
                isMoreSymbolsMode = true
                updateNumberSymbolsKeyboard()
            }
            KeyAction.SwitchAlpha -> {
                restoreAlphaKeyboard()
            }
            KeyAction.ToggleBengaliDigits -> {
                isBengaliDigitsMode = !isBengaliDigitsMode
                updateNumberSymbolsKeyboard()
            }
            KeyAction.SwitchLayout -> cycleLayout()
            KeyAction.VoiceTyping  -> startVoiceTyping()
            KeyAction.SwitchEmoji  -> setInputViewMode(InputViewMode.EMOJI)
            KeyAction.SwitchClipboard -> setInputViewMode(InputViewMode.CLIPBOARD)
            KeyAction.CursorLeft -> handleCursorMove(-1)
            KeyAction.CursorRight -> handleCursorMove(1)
            KeyAction.Tab -> sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            KeyAction.ToggleGboardVowels -> {
                val current = keyboardView?.isGboardKarsActive ?: false
                keyboardView?.setGboardKarsActive(!current, consonant = "")
            }
        }
    }

    private fun flushComposing(ic: InputConnection) {
        if (session.isComposing() || preeditShadow.isNotEmpty()) {
            ic.beginBatchEdit()
            try {
                ic.finishComposingText()
                session.reset()
                preeditShadow = ""
                rawInputBuffer.clear()
                clearCandidates()
                clearUndo()
            } finally {
                ic.endBatchEdit()
            }
            val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
            if (!before.isNullOrEmpty()) {
                session.setContext(before)
            }
        }
    }

    private fun handleCharacterInput(key: Key, token: String) {
        val ic = currentInputConnection ?: return
        val isNumericActive = isNumericMode || isNumericFieldMode || isPhoneDialpadMode

        if (isNumericActive) {
            // When in numeric / numpad / symbol layer:
            if (!isBengaliDigitsMode) {
                // English digits mode (1, 2, 3...) or English symbol mode
                if (token.length == 1 && token[0] in '0'..'9') {
                    flushComposing(ic)
                    ic.commitText(token, 1)
                    val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
                    if (!before.isNullOrEmpty()) {
                        session.setContext(before)
                    }
                    updateAutoCaps()
                    return
                }
            } else {
                // Bengali digits mode (১, ২, ৩...)
                if (token.length == 1 && token[0] in '0'..'9') {
                    val bnDigit = ('\u09E6' + (token[0] - '0')).toString()
                    flushComposing(ic)
                    ic.commitText(bnDigit, 1)
                    val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
                    if (!before.isNullOrEmpty()) {
                        session.setContext(before)
                    }
                    updateAutoCaps()
                    return
                }
                if (token.length == 1 && token[0] in '\u09E6'..'\u09EF') {
                    flushComposing(ic)
                    ic.commitText(token, 1)
                    val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
                    if (!before.isNullOrEmpty()) {
                        session.setContext(before)
                    }
                    updateAutoCaps()
                    return
                }
            }
        } else if (session.getLayout() == LekhaniLayoutType.AVRO) {
            // On Avro layout:
            val isDigit = token.length == 1 && token[0] in '0'..'9'
            val isBengaliDigit = token.length == 1 && token[0] in '\u09E6'..'\u09EF'

            if (isDigit) {
                // Tapped on dedicated number row (1..0), or long-pressed q..p (hint 1..0)
                val targetText = if (keyboardPrefs.avroNumeralsBengali) {
                    ('\u09E6' + (token[0] - '0')).toString()
                } else {
                    token
                }
                flushComposing(ic)
                ic.commitText(targetText, 1)
                val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
                if (!before.isNullOrEmpty()) {
                    session.setContext(before)
                }
                updateAutoCaps()
                return
            } else if (isBengaliDigit) {
                // Long-press on dedicated number row (hold 1 -> ১) or explicit Bengali numeral
                flushComposing(ic)
                ic.commitText(token, 1)
                val before = try { ic.getTextBeforeCursor(64, 0)?.toString() } catch (_: Exception) { null }
                if (!before.isNullOrEmpty()) {
                    session.setContext(before)
                }
                updateAutoCaps()
                return
            }
        }

        onKey(token)
    }

    private fun crossfadeViewMode(activeView: View?, vararg otherViews: View?) {
        for (view in otherViews) {
            view?.animate()?.cancel()
            view?.visibility = View.GONE
            view?.alpha = 1f
        }
        activeView?.let { view ->
            view.animate()?.cancel()
            view.alpha = 1f
            view.visibility = View.VISIBLE
        }
    }

    /**
     * Toggles between Keyboard Canvas, Emoji/Symbol Picker, and Clipboard History.
     */
    fun setInputViewMode(mode: InputViewMode, initialQuery: String = "") {
        _showQuickLayoutPickerFlow.value = false
        currentMode = mode
        _inputViewModeFlow.value = mode
        val container = modesContainer ?: return
        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)

        val stripHeightPx = if (lastMeasuredStripHeightPx > 0) {
            lastMeasuredStripHeightPx
        } else if ((candidateStripComposeView?.height ?: 0) > 0) {
            candidateStripComposeView!!.height
        } else {
            (44 * resources.displayMetrics.density).toInt()
        }
        val totalKeyboardHeightPx = if (lastMeasuredKeyboardHeightPx > 0) {
            lastMeasuredKeyboardHeightPx + stripHeightPx
        } else {
            (304 * resources.displayMetrics.density).toInt()
        }
        val kbHeightDp = (totalKeyboardHeightPx / resources.displayMetrics.density).dp
        _paletteHeightDp.value = kbHeightDp

        when (mode) {
            InputViewMode.KEYBOARD -> {
                keyboardView?.isResizeVisualGuide = false
                resizeOverlayComposeView?.visibility = View.GONE
                crossfadeViewMode(keyboardView, emojiPickerView, clipboardView, textEditorView, toolsMenuView)
                candidateStripComposeView?.visibility = View.VISIBLE
                emojiSearchQuery = ""
                emojiSearchRawQuery = ""
                emojiSearchSession = null
                clearCandidates()
                updateCandidatesVisibility()
            }
            InputViewMode.EMOJI_SEARCH -> {
                keyboardView?.isResizeVisualGuide = false
                resizeOverlayComposeView?.visibility = View.GONE
                crossfadeViewMode(keyboardView, emojiPickerView, clipboardView, textEditorView, toolsMenuView)
                candidateStripComposeView?.visibility = View.VISIBLE
                emojiSearchQuery = initialQuery
                emojiSearchRawQuery = initialQuery
                val searchSession = emojiSearchSession ?: AndroidLekhaniSession().also {
                    emojiSearchSession = it
                }
                searchSession.reset()
                searchSession.setLayout(session.getLayout())
                updateEmojiSearchStrip()
            }
            InputViewMode.EMOJI -> {
                keyboardView?.isResizeVisualGuide = false
                resizeOverlayComposeView?.visibility = View.GONE
                candidateStripComposeView?.visibility = View.GONE
                emojiSearchQuery = ""
                emojiSearchRawQuery = ""
                emojiSearchSession = null

                if (emojiPickerView == null || emojiPickerView?.parent != container) {
                    (emojiPickerView?.parent as? ViewGroup)?.removeView(emojiPickerView)
                    emojiPickerView = null
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val theme by _themeFlow.collectAsState()
                            val heightDp by _paletteHeightDp.collectAsState()
                            EmojiPickerView(
                                recentsManager = recentsManager,
                                theme = theme,
                                isEnglish = (keyboardPrefs.uiLanguage == "en"),
                                paletteHeight = heightDp,
                                onEmojiSelected = { emoji ->
                                    emojiPickerView?.let { feedbackManager.onKeyFeedback(it) }
                                    currentInputConnection?.commitText(emoji, 1)
                                },
                                onBackspace = { onBackspace() },
                                onSpace = { onSpace() },
                                onClose = { setInputViewMode(InputViewMode.KEYBOARD) },
                                onSearchClick = { query ->
                                    setInputViewMode(InputViewMode.EMOJI_SEARCH, query)
                                },
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
                crossfadeViewMode(emojiPickerView, keyboardView, clipboardView, textEditorView, toolsMenuView)
            }
            InputViewMode.CLIPBOARD -> {
                candidateStripComposeView?.visibility = View.GONE
                emojiSearchQuery = ""
                emojiSearchSession = null
                syncSystemClipboard()

                if (clipboardView == null || clipboardView?.parent != container) {
                    (clipboardView?.parent as? ViewGroup)?.removeView(clipboardView)
                    clipboardView = null
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val theme by _themeFlow.collectAsState()
                            val heightDp by _paletteHeightDp.collectAsState()
                            ClipboardSheetView(
                                clipboardStore = clipboardStore,
                                theme = theme,
                                sheetHeight = heightDp,
                                isEnglish = (keyboardPrefs.uiLanguage == "en"),
                                onPaste = { text ->
                                    runCatching { currentInputConnection?.commitText(text, 1) }
                                    setInputViewMode(InputViewMode.KEYBOARD)
                                },
                                onOpenEditor = { openClipboardEditor() },
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
                crossfadeViewMode(clipboardView, keyboardView, emojiPickerView, textEditorView, toolsMenuView)
            }
            InputViewMode.TEXT_EDITOR -> {
                candidateStripComposeView?.visibility = View.GONE
                emojiSearchQuery = ""
                emojiSearchSession = null

                if (textEditorView == null || textEditorView?.parent != container) {
                    (textEditorView?.parent as? ViewGroup)?.removeView(textEditorView)
                    textEditorView = null
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val theme by _themeFlow.collectAsState()
                            val heightDp by _paletteHeightDp.collectAsState()
                            TextEditorSheetView(
                                theme = theme,
                                sheetHeight = heightDp,
                                isEnglish = (keyboardPrefs.uiLanguage == "en"),
                                onMoveLeft = { select -> sendEditorNavKey(KeyEvent.KEYCODE_DPAD_LEFT, select) },
                                onMoveRight = { select -> sendEditorNavKey(KeyEvent.KEYCODE_DPAD_RIGHT, select) },
                                onMoveUp = { select -> sendEditorNavKey(KeyEvent.KEYCODE_DPAD_UP, select) },
                                onMoveDown = { select -> sendEditorNavKey(KeyEvent.KEYCODE_DPAD_DOWN, select) },
                                onMoveHome = { select -> sendEditorNavKey(KeyEvent.KEYCODE_MOVE_HOME, select) },
                                onMoveEnd = { select -> sendEditorNavKey(KeyEvent.KEYCODE_MOVE_END, select) },
                                onSelectAll = {
                                    editorSelectionAnchor = -1
                                    val ic = currentInputConnection ?: return@TextEditorSheetView
                                    if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                                        sendEditorKeyWithMeta(KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON)
                                    }
                                },
                                onCut = {
                                    editorSelectionAnchor = -1
                                    val ic = currentInputConnection
                                    ic?.performContextMenuAction(android.R.id.cut)
                                    updateCandidatesVisibility()
                                },
                                onCopy = {
                                    editorSelectionAnchor = -1
                                    val ic = currentInputConnection
                                    ic?.performContextMenuAction(android.R.id.copy)
                                    val end = maxOf(currentSelStart, currentSelEnd)
                                    if (end >= 0) {
                                        ic?.setSelection(end, end)
                                    }
                                    updateCandidatesVisibility()
                                },
                                onPaste = {
                                    editorSelectionAnchor = -1
                                    val ic = currentInputConnection
                                    ic?.performContextMenuAction(android.R.id.paste)
                                    updateCandidatesVisibility()
                                },
                                onBackspace = { onBackspace() },
                                onEnter = { commitEnter() },
                                onClose = {
                                    editorSelectionAnchor = -1
                                    setInputViewMode(InputViewMode.KEYBOARD)
                                },
                            )
                        }
                    }
                    textEditorView = compose
                    container.addView(
                        compose,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                crossfadeViewMode(textEditorView, keyboardView, emojiPickerView, clipboardView, toolsMenuView)
            }
            InputViewMode.RESIZE -> {
                candidateStripComposeView?.visibility = View.GONE
                emojiSearchQuery = ""
                emojiSearchSession = null
                clearCandidates()

                crossfadeViewMode(keyboardView, emojiPickerView, clipboardView, textEditorView, toolsMenuView)
                keyboardView?.isResizeVisualGuide = true

                val initialScale = keyboardPrefs.heightScale
                resizeOverlayComposeView?.setContent {
                    com.lekhani.android.ui.resize.KeyboardResizeOverlayView(
                        initialScale = initialScale,
                        theme = activeTheme,
                        isEnglish = (keyboardPrefs.uiLanguage == "en"),
                        onScaleLiveChange = { liveScale ->
                            keyboardView?.setLiveHeightScale(liveScale)
                        },
                        onConfirm = { confirmedScale ->
                            keyboardPrefs.heightScale = confirmedScale
                            setInputViewMode(InputViewMode.KEYBOARD)
                            keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
                        },
                        onDismiss = {
                            keyboardView?.setLiveHeightScale(initialScale)
                            setInputViewMode(InputViewMode.KEYBOARD)
                            keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
                        },
                    )
                }
                resizeOverlayComposeView?.visibility = View.VISIBLE
            }
            InputViewMode.TOOLS_MENU -> {
                keyboardView?.isResizeVisualGuide = false
                resizeOverlayComposeView?.visibility = View.GONE
                candidateStripComposeView?.visibility = View.VISIBLE
                emojiSearchQuery = ""
                emojiSearchSession = null
                clearCandidates()

                val minMenuHeight = (265 * resources.displayMetrics.density).toInt()
                val kbHeight = maxOf(keyboardView?.height ?: 0, minMenuHeight)

                if (toolsMenuView == null || toolsMenuView?.parent != container) {
                    (toolsMenuView?.parent as? ViewGroup)?.removeView(toolsMenuView)
                    toolsMenuView = null
                    val compose = ComposeView(this).apply {
                        attachLifecycleOwner(this)
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                        setContent {
                            val theme by _themeFlow.collectAsState()
                            ExtraToolsSheetView(
                                theme = theme,
                                prefs = keyboardPrefs,
                                isEnglish = (keyboardPrefs.uiLanguage == "en"),
                                onToolSelected = { tool ->
                                    setInputViewMode(InputViewMode.KEYBOARD)
                                    handleToolbarToolClick(tool)
                                },
                                onToolsUpdated = { newTools ->
                                    _toolsFlow.value = newTools
                                },
                                onClose = {
                                    setInputViewMode(InputViewMode.KEYBOARD)
                                }
                            )
                        }
                    }
                    toolsMenuView = compose
                    container.addView(
                        compose,
                        FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            kbHeight
                        )
                    )
                } else {
                    toolsMenuView?.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        kbHeight
                    )
                }
                crossfadeViewMode(toolsMenuView, keyboardView, emojiPickerView, clipboardView, textEditorView)
            }
        }
    }

    private fun updateNumberSymbolsKeyboard() {
        val currentLayoutType = session.getLayout()
        val isEnglish = currentLayoutType == LekhaniLayoutType.ENGLISH
        val layout = when {
            isPhoneDialpadMode -> NumberSymbolsLayout.phoneDialpadLayout
            isNumericFieldMode -> if (isBengaliDigitsMode) NumberSymbolsLayout.bengaliNumpadPinLayout else NumberSymbolsLayout.numpadPinLayout
            isMoreSymbolsMode -> NumberSymbolsLayout.moreSymbolsLayout
            isEnglish -> NumberSymbolsLayout.englishNumericLayout
            isBengaliDigitsMode -> NumberSymbolsLayout.bengaliNumericLayout
            else -> NumberSymbolsLayout.numericLayout
        }
        keyboardView?.setLayout(layout, currentLayoutType, shifted = false)
        keyboardView?.setGboardKarsActive(false)
        updateCandidatesVisibility()
    }

    private fun restoreAlphaKeyboard() {
        isNumericMode = false
        isMoreSymbolsMode = false
        isNumericFieldMode = false
        isPhoneDialpadMode = false
        val currentLayoutType = previousLayoutBeforeNumeric ?: session.getLayout()
        previousLayoutBeforeNumeric = null
        session.setLayout(currentLayoutType)
        emojiSearchSession?.setLayout(currentLayoutType)
        keyboardView?.setLayout(LayoutRegistry.get(currentLayoutType), currentLayoutType, shifted = false)
        keyboardView?.setGboardKarsActive(false)
        updateCandidatesVisibility()
    }

    private fun sendEditorNavKey(keyCode: Int, isShift: Boolean) {
        val ic = currentInputConnection ?: return
        if (!isShift) {
            editorSelectionAnchor = -1
            sendDownUpKeyEvents(keyCode)
            return
        }

        // Resolution order for the selection anchor:
        // 1. If onUpdateSelection has already given us fresh coords, use them directly.
        // 2. Otherwise call getExtractedText (most editors) or fall back to getTextBeforeCursor length.
        // This avoids anchor defaulting to 0 when the user opens Text Editor and taps Select immediately.
        if (currentSelStart < 0 || currentSelEnd < 0) {
            val extracted = try {
                ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
            } catch (_: Exception) {
                null
            }
            if (extracted != null) {
                currentSelStart = extracted.selectionStart
                currentSelEnd = extracted.selectionEnd
            } else {
                val beforeLen = try {
                    ic.getTextBeforeCursor(10000, 0)?.length ?: 0
                } catch (_: Exception) {
                    0
                }
                currentSelStart = beforeLen
                currentSelEnd = beforeLen
            }
        }

        if (editorSelectionAnchor < 0) {
            // Use the moving end of an existing selection as the anchor point,
            // or the cursor position if no selection exists.
            editorSelectionAnchor = when {
                currentSelEnd >= 0 -> currentSelEnd
                currentSelStart >= 0 -> currentSelStart
                else -> 0  // genuine fallback only when both are unresolvable
            }
        }


        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                val currentEnd = if (currentSelEnd >= 0) currentSelEnd else editorSelectionAnchor
                val newEnd = (currentEnd - 1).coerceAtLeast(0)
                currentSelEnd = newEnd
                val selStart = minOf(editorSelectionAnchor, newEnd)
                val selEnd = maxOf(editorSelectionAnchor, newEnd)
                ic.setSelection(selStart, selEnd)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val currentEnd = if (currentSelEnd >= 0) currentSelEnd else editorSelectionAnchor
                val newEnd = currentEnd + 1
                currentSelEnd = newEnd
                val selStart = minOf(editorSelectionAnchor, newEnd)
                val selEnd = maxOf(editorSelectionAnchor, newEnd)
                ic.setSelection(selStart, selEnd)
            }
            KeyEvent.KEYCODE_MOVE_HOME -> {
                val newEnd = 0
                currentSelEnd = newEnd
                val selStart = minOf(editorSelectionAnchor, newEnd)
                val selEnd = maxOf(editorSelectionAnchor, newEnd)
                ic.setSelection(selStart, selEnd)
            }
            KeyEvent.KEYCODE_MOVE_END -> {
                val textAfter = try {
                    ic.getTextAfterCursor(10000, 0)?.length ?: 0
                } catch (_: Exception) {
                    0
                }
                val currentEnd = if (currentSelEnd >= 0) currentSelEnd else editorSelectionAnchor
                val newEnd = currentEnd + textAfter
                currentSelEnd = newEnd
                val selStart = minOf(editorSelectionAnchor, newEnd)
                val selEnd = maxOf(editorSelectionAnchor, newEnd)
                ic.setSelection(selStart, selEnd)
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                // Dispatch hardware Shift + Key sequence for vertical multiline selection
                val now = android.os.SystemClock.uptimeMillis()
                val meta = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
            }
            else -> {
                sendEditorKeyWithMeta(keyCode, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            }
        }
    }

    private fun sendEditorKeyWithMeta(keyCode: Int, metaState: Int) {
        val ic = currentInputConnection ?: return
        val now = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    private fun openClipboardEditor() {
        try {
            val intent = Intent(this, com.lekhani.android.ui.LekhaniSettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(com.lekhani.android.ui.LekhaniSettingsActivity.EXTRA_OPEN_CLIPBOARD, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch clipboard editor: ${e.message}")
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (currentMode != InputViewMode.KEYBOARD) {
            setInputViewMode(InputViewMode.KEYBOARD)
        }
        keyboardView?.cancelSwipeDelete()
        if (isSwipeDeleteActive) {
            isSwipeDeleteActive = false
            swipeDeleteAnchorCursor = -1
            activeSwipeDeletePreviewText = ""
            activeSwipeSnapshotText = ""
        }
        if (_candidateState.value is CandidateStripState.SwipeDeletePreview ||
            _candidateState.value is CandidateStripState.Selection) {
            _candidateState.value = CandidateStripState.Empty
        }
        persistUserLearnedAsync()
    }


    private fun updateCandidatesVisibility() {
        val isSymbols = isNumericMode || isMoreSymbolsMode
        val show = (currentMode == InputViewMode.KEYBOARD && !isSymbols) ||
                   currentMode == InputViewMode.EMOJI_SEARCH ||
                   currentMode == InputViewMode.TOOLS_MENU
        candidateStripComposeView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateEmojiSearchStrip() {
        val q1 = emojiSearchQuery.trim()
        val q2 = emojiSearchRawQuery.trim()
        val results = if (q1.isBlank() && q2.isBlank()) {
            val recents = recentsManager.getRecents()
            if (recents.isNotEmpty()) recents else EmojiData.defaultPopularEmojis
        } else {
            val res1 = if (q1.isNotBlank()) EmojiData.search(q1) else emptyList()
            val res2 = if (q2.isNotBlank() && q2.lowercase() != q1.lowercase()) EmojiData.search(q2) else emptyList()
            (res1 + res2).map { it.emoji }.distinct().take(40)
        }
        val displayQuery = if (q1.isNotBlank()) q1 else q2
        _candidateState.value = CandidateStripState.EmojiSearch(
            query = displayQuery,
            emojis = results
        )
    }

    /**
     * Starts offline voice typing capture and displays waveform overlay.
     */
    private fun startVoiceTyping() {
        if (!audioManager.hasRecordPermission()) {
            Log.w(TAG, "RECORD_AUDIO permission not granted; cannot start voice typing")
            val isEnglish = keyboardPrefs.uiLanguage == "en"
            val msg = if (isEnglish) "Microphone permission required for voice typing"
                      else "ভয়েস টাইপিংয়ের জন্য মাইক্রোফোন অনুমতি প্রয়োজন"
            showNotice(msg, Icons.Filled.Mic)
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

    private fun commitEnter(forceNewline: Boolean = false) {
        val ic = currentInputConnection ?: return
        if (session.isComposing() || preeditShadow.isNotEmpty()) {
            ic.finishComposingText()
            session.reset()
            preeditShadow = ""
            rawInputBuffer.clear()
            clearCandidates()
            clearUndo()
        }
        val info = currentInputEditorInfo
        val isShiftActive = forceNewline || (keyboardView?.isShiftActive == true)

        val behavior = EnterKeyResolver.determineBehavior(info, isShiftActive)
        when (behavior) {
            EnterKeyResolver.Behavior.NEWLINE -> {
                if (!ic.commitText("\n", 1)) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
            EnterKeyResolver.Behavior.ACTION -> {
                val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
                val handled = if (action != EditorInfo.IME_ACTION_UNSPECIFIED && action != EditorInfo.IME_ACTION_NONE) {
                    ic.performEditorAction(action)
                } else {
                    false
                }
                if (!handled) {
                    if (!ic.commitText("\n", 1)) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    }
                }
            }
            EnterKeyResolver.Behavior.RAW_KEY -> {
                if (info?.inputType == InputType.TYPE_NULL) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                } else if (!sendDefaultEditorAction(true)) {
                    if (!ic.commitText("\n", 1)) {
                        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                    }
                }
            }
        }
        session.reset()
        keyboardView?.setShifted(false)
        keyboardView?.setGboardKarsActive(false)
        updateAutoCaps()
        updateEnterActionAndFieldType(currentInputEditorInfo)
    }

    fun getEnabledLayouts(): List<LekhaniLayoutType> {
        val saved = devicePrefs.getString(LayoutRegistry.PREF_ENABLED_LAYOUTS, null)
        return LayoutRegistry.parseEnabledLayouts(saved)
    }

    fun getSavedLayout(): LekhaniLayoutType {
        val enabledList = getEnabledLayouts()
        return devicePrefs.getString(PREF_LAYOUT, null)
            ?.let { runCatching { LekhaniLayoutType.valueOf(it) }.getOrNull() }
            ?.takeIf { enabledList.contains(it) }
            ?: (if (enabledList.contains(LayoutRegistry.DEFAULT_ACTIVE_LAYOUT)) LayoutRegistry.DEFAULT_ACTIVE_LAYOUT else enabledList.firstOrNull())
            ?: LayoutRegistry.DEFAULT_ACTIVE_LAYOUT
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
        val container = modesContainer ?: return
        if (quickLayoutPickerComposeView == null) {
            val compose = ComposeView(this).apply {
                attachLifecycleOwner(this)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    val isVisible by _showQuickLayoutPickerFlow.collectAsState()
                    val theme by _themeFlow.collectAsState()
                    val enabledLayouts = remember(isVisible) { getEnabledLayouts() }
                    val currentLayout = session.getLayout()

                    QuickLayoutPickerSheet(
                        visible = isVisible,
                        theme = theme,
                        enabledLayouts = enabledLayouts,
                        currentLayout = currentLayout,
                        isEnglish = (keyboardPrefs.uiLanguage == "en"),
                        onSelectLayout = { selected ->
                            switchLayout(selected)
                            _showQuickLayoutPickerFlow.value = false
                            quickLayoutPickerComposeView?.visibility = View.GONE
                        },
                        onOpenTextEditor = {
                            _showQuickLayoutPickerFlow.value = false
                            quickLayoutPickerComposeView?.visibility = View.GONE
                            setInputViewMode(InputViewMode.TEXT_EDITOR)
                        },
                        onDismiss = {
                            _showQuickLayoutPickerFlow.value = false
                            quickLayoutPickerComposeView?.visibility = View.GONE
                        }
                    )
                }
            }
            quickLayoutPickerComposeView = compose
            container.addView(
                compose,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
        quickLayoutPickerComposeView?.visibility = View.VISIBLE
        _showQuickLayoutPickerFlow.value = true
    }

    // Physical / Bluetooth Hardware Keyboard Integration (Phase 7)

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Intercept back key when inside Quick Layout Picker, Emoji or Clipboard view to return to Keyboard
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (_showQuickLayoutPickerFlow.value) {
                _showQuickLayoutPickerFlow.value = false
                quickLayoutPickerComposeView?.visibility = View.GONE
                consumedBackOnKeyDown = true
                return true
            }
            if (currentMode == InputViewMode.EMOJI_SEARCH) {
                setInputViewMode(InputViewMode.EMOJI)
                consumedBackOnKeyDown = true
                return true
            }
            if (currentMode != InputViewMode.KEYBOARD) {
                setInputViewMode(InputViewMode.KEYBOARD)
                consumedBackOnKeyDown = true
                return true
            }
            consumedBackOnKeyDown = false
            return super.onKeyDown(keyCode, event)
        }

        // Allow system shortcuts (Ctrl+C, Ctrl+V, Alt+Tab, Home, etc.) to pass through
        if (event.isCtrlPressed || event.isAltPressed) {
            return super.onKeyDown(keyCode, event)
        }

        // Volume key cursor navigation (optional power-user preference)
        val volumeKeyMode = keyboardPrefs.volumeKeyCursorMode
        if (volumeKeyMode != KeyboardPreferences.VolumeKeyCursorMode.DISABLED && isInputViewShown && currentInputConnection != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val delta = if (volumeKeyMode == KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT) -1 else 1
                    handleCursorMove(delta)
                    keyboardView?.let { feedbackManager.onTickFeedback(it) }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val delta = if (volumeKeyMode == KeyboardPreferences.VolumeKeyCursorMode.UP_LEFT_DOWN_RIGHT) 1 else -1
                    handleCursorMove(delta)
                    keyboardView?.let { feedbackManager.onTickFeedback(it) }
                    return true
                }
            }
        }

        // Handle physical keyboard input while in Emoji Search mode
        if (currentMode == InputViewMode.EMOJI_SEARCH) {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                val sSession = emojiSearchSession
                if (sSession != null && sSession.isComposing()) {
                    val res = try { sSession.handleBackspace() } catch (e: Exception) { null }
                    emojiSearchQuery = res?.preedit ?: ""
                    if (emojiSearchRawQuery.isNotEmpty()) {
                        emojiSearchRawQuery = emojiSearchRawQuery.dropLast(1)
                    }
                    updateEmojiSearchStrip()
                } else if (emojiSearchQuery.isNotEmpty() || emojiSearchRawQuery.isNotEmpty()) {
                    if (emojiSearchQuery.isNotEmpty()) {
                        emojiSearchQuery = emojiSearchQuery.dropLast(1)
                    }
                    if (emojiSearchRawQuery.isNotEmpty()) {
                        emojiSearchRawQuery = emojiSearchRawQuery.dropLast(1)
                    }
                    updateEmojiSearchStrip()
                } else {
                    setInputViewMode(InputViewMode.EMOJI)
                }
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_SPACE) {
                val sSession = emojiSearchSession
                if (sSession != null && sSession.isComposing()) {
                    val res = try { sSession.handleSpace() } catch (e: Exception) { null }
                    if (res != null && !res.commitText.isNullOrEmpty()) {
                        emojiSearchQuery = res.commitText + " "
                    } else {
                        emojiSearchQuery += " "
                    }
                } else {
                    emojiSearchQuery += " "
                }
                emojiSearchRawQuery += " "
                updateEmojiSearchStrip()
                return true
            }
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                val cur = _candidateState.value
                if (cur is CandidateStripState.EmojiSearch && cur.emojis.isNotEmpty()) {
                    onCandidateSelected(cur.emojis.first())
                } else {
                    setInputViewMode(InputViewMode.KEYBOARD)
                }
                return true
            }
            val unicode = event.unicodeChar
            if (unicode > 0 && !Character.isISOControl(unicode)) {
                val charStr = unicode.toChar().toString()
                emojiSearchRawQuery += charStr
                val sSession = emojiSearchSession ?: AndroidLekhaniSession().apply {
                    setLayout(session.getLayout())
                    emojiSearchSession = this
                }
                val res = try { sSession.processKey(charStr) } catch (e: Exception) { null }
                if (res != null) {
                    if (res.preedit.isNotEmpty()) {
                        emojiSearchQuery = res.preedit
                    } else if (!res.commitText.isNullOrEmpty()) {
                        emojiSearchQuery += res.commitText
                    } else {
                        emojiSearchQuery += charStr
                    }
                } else {
                    emojiSearchQuery += charStr
                }
                updateEmojiSearchStrip()
                return true
            }
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
                commitEnter(forceNewline = event.isShiftPressed)
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
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (consumedBackOnKeyDown) {
                consumedBackOnKeyDown = false
                return true
            }
        }
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            keyboardView?.setShifted(false)
        }
        val volumeKeyMode = keyboardPrefs.volumeKeyCursorMode
        if (volumeKeyMode != KeyboardPreferences.VolumeKeyCursorMode.DISABLED && isInputViewShown && currentInputConnection != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    // Fullscreen mode policy

    /**
     * ALWAYS returns false.
     *
     * The default Android IME behaviour in landscape mode is to take over the
     * entire screen with a "full-screen extract UI" that hides the app content.
     * This is disorienting and breaks context-dependent typing (e.g., replying
     * to a message you can no longer read). Lekhani always overlays as a panel.
     */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return true
    }

    // Key processing  (hot path — must not block the main thread)

    /**
     * Checks if the user manually repositioned the cursor away from the active
     * composing text before IPC delivered onUpdateSelection. If so, cleanly
     * finalizes the existing composition at its current location and resets state.
     */
    private fun ensureCursorInComposingRegion(ic: InputConnection) {
        if (!session.isComposing() && preeditShadow.isEmpty()) return

        val textBefore = try {
            ic.getTextBeforeCursor(preeditShadow.length + 16, 0)?.toString()
        } catch (_: Exception) {
            null
        } ?: return

        // In Chromium WebViews, Compose, or Flutter, getTextBeforeCursor may return empty
        // or return text preceding the composing span. Never wipe composing on empty text.
        if (textBefore.isEmpty()) return

        if (!textBefore.endsWith(preeditShadow) && !textBefore.contains(preeditShadow)) {
            if (textBefore.length >= preeditShadow.length) {
                ic.finishComposingText()
                session.reset()
                preeditShadow = ""
                rawInputBuffer.clear()
                clearCandidates()
                clearUndo()
            }
        }
    }

    /**
     * Checks if a key token is a character that contributes to an Avro phonetic composing word.
     */
    private fun isAvroPhoneticKey(keyToken: String): Boolean {
        if (keyToken.length != 1) return false
        val c = keyToken[0]
        return (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') || c == ':' || c == '^' || c == '`'
    }

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
        // User is now typing the next word — safe to show next-word predictions again.
        suppressNextWordAfterCommit = false
        clearUndo()
        isQuickChipDismissed = true
        val ic = currentInputConnection ?: return
        ensureCursorInComposingRegion(ic)

        // 0. Selection-aware text replacement:
        // If text is selected in the target editor, typing any key immediately overwrites the selection
        val hasSelection = (currentSelStart != currentSelEnd && currentSelStart >= 0 && currentSelEnd >= 0)
        if (hasSelection) {
            ic.commitText("", 1)
            session.reset()
            preeditShadow = ""
            rawInputBuffer.clear()
            activeInspectedWord = null
            currentSelStart = -1
            currentSelEnd = -1
            clearCandidates()
        }

        // 0a. Smart Punctuation Spacing Auto-Collapse:
        // If a word was just committed with a trailing space and the user taps punctuation within 2.5s,
        // absorb the preceding space and cleanly attach the punctuation.
        if (keyboardPrefs.smartPunctuationSpacing && keyToken.length == 1 && keyToken[0] in SMART_PUNCTUATION_CHARS) {
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastCommitWithTrailingSpaceTime <= 2500) {
                val before = try { ic.getTextBeforeCursor(2, 0)?.toString() } catch (_: Exception) { null }
                if (before != null && before.endsWith(" ")) {
                    ic.beginBatchEdit()
                    try {
                        ic.deleteSurroundingText(1, 0)
                        val insertText = if (keyToken in listOf("।", "॥", ",", ";", ":", "!", "?")) "$keyToken " else keyToken
                        ic.commitText(insertText, 1)
                    } finally {
                        ic.endBatchEdit()
                    }
                    lastCommitWithTrailingSpaceTime = 0L
                    refreshSurroundingContext()
                    return
                }
            }
        }

        // 0b. Orphan Vowel Kar & Diacritic Healing:
        // When deleting a base consonant leaves an orphan vowel kar right after the cursor (e.g. '|ান'),
        // typing a new consonant key seamlessly absorbs the orphan kar and fuses them (e.g. 'p' + 'া' -> 'পা' + 'ন' = 'পান').
        val charAfter = try { ic.getTextAfterCursor(1, 0)?.toString()?.firstOrNull() } catch (_: Exception) { null }
        if (charAfter != null && charAfter in BENGALI_VOWEL_KARS &&
            !session.isComposing() && preeditShadow.isEmpty() &&
            session.getLayout() == LekhaniLayoutType.AVRO &&
            !isCurrentFieldPrivate && isAvroPhoneticKey(keyToken)
        ) {
            val mappedVowel = BENGALI_VOWEL_KARS[charAfter] ?: ""
            val isConsonant = keyToken.length == 1 && keyToken[0].lowercaseChar() !in listOf('a', 'e', 'i', 'o', 'u')
            if (mappedVowel.isNotEmpty() && isConsonant) {
                ic.deleteSurroundingText(0, 1)
                rawInputBuffer.clear()
                rawInputBuffer.append(keyToken).append(mappedVowel)
                session.reset()
                val immediateBefore = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null }
                if (!immediateBefore.isNullOrEmpty()) {
                    session.setContext(immediateBefore)
                }
                var result: com.lekhani.android.ffi.TypingResult? = null
                for (i in 0 until rawInputBuffer.length) {
                    result = session.processKey(rawInputBuffer[i].toString())
                }
                if (result != null) {
                    preeditShadow = result.preedit
                    setComposingTextSafe(ic, result.preedit)
                    publishCandidates(result.candidates)
                }
                return
            }
        }

        val inspected = activeInspectedWord
        if (inspected != null && !session.isComposing() &&
            session.getLayout() == LekhaniLayoutType.AVRO &&
            !isCurrentFieldPrivate && keyboardPrefs.avroDynamicRecomposition &&
            isAvroPhoneticKey(keyToken)
        ) {
            val rawPrefix = if (inspected.charsAfterCursor == 0) {
                inspected.rawPhonetic
            } else {
                val prefixText = inspected.word.take(inspected.charsBeforeCursor)
                avroHistory.get(prefixText) ?: AvroReverseTransliterator.bengaliToAvro(prefixText)
            }

            if (rawPrefix.isNotBlank()) {
                activeInspectedWord = null
                ic.beginBatchEdit()
                try {
                    if (inspected.charsBeforeCursor > 0) {
                        ic.deleteSurroundingText(inspected.charsBeforeCursor, 0)
                    }
                    rawInputBuffer.clear()
                    rawInputBuffer.append(rawPrefix).append(keyToken)

                    session.reset()
                    val immediateBefore = try {
                        ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString()
                    } catch (_: Exception) { null }
                    if (!immediateBefore.isNullOrEmpty()) {
                        session.setContext(immediateBefore)
                    }

                    var result: com.lekhani.android.ffi.TypingResult? = null
                    for (i in 0 until rawInputBuffer.length) {
                        result = session.processKey(rawInputBuffer[i].toString())
                    }

                    if (result != null) {
                        result.commitText?.let { commitTxt ->
                            ic.commitText(commitTxt, 1)
                            preeditShadow = ""
                            rawInputBuffer.clear()
                            publishCandidates(result.candidates)
                        } ?: run {
                            preeditShadow = result.preedit
                            setComposingTextSafe(ic, result.preedit)
                            publishCandidates(result.candidates)
                        }
                    }
                    return
                } finally {
                    ic.endBatchEdit()
                }
            }
            activeInspectedWord = null
            clearCandidates()
        } else if (activeInspectedWord != null && !session.isComposing()) {
            activeInspectedWord = null
            clearCandidates()
        }

        if (!session.isComposing() && preeditShadow.isEmpty()) {
            rawInputBuffer.clear()
        }
        rawInputBuffer.append(keyToken)

        // Ensure the English dictionary is loaded, but never block the main thread.
        // The check itself is fast (boolean read); the actual load is dispatched to IO only
        // when the dict is missing, which is rare (once per session after layout switch).
        if (session.getLayout() == LekhaniLayoutType.ENGLISH && !isEnglishDictLoaded) {
            serviceScope.launch(Dispatchers.IO) { ensureEnglishDictionaryLoaded() }
        }

        // If not actively composing, ensure engine has fresh preceding text before cursor
        // so context-aware vowel promotion (e.g. Probaho 'মনুষ' + 'া' -> 'মনুষা', NOT 'মনুষআ')
        // and conjunct suggestions have the exact preceding character.
        if (!session.isComposing() && preeditShadow.isEmpty()) {
            val immediateBefore = try {
                ic.getTextBeforeCursor(64, 0)?.toString()
            } catch (_: Exception) {
                null
            }
            if (!immediateBefore.isNullOrEmpty()) {
                session.setContext(immediateBefore)
            }
        }

        val touch = lastTouchCoordinates
        lastTouchCoordinates = null
        val result = try {
            if (touch != null) {
                session.processKeyWithTouch(keyToken, touch.first, touch.second)
            } else {
                session.processKey(keyToken)
            }
        } catch (e: LekhaniException) {
            Log.e(TAG, "processKey error for '$keyToken': $e")
            return
        }

        updateGboardDynamicRow(keyToken)

        result.commitText?.let { rawText ->
            val text = if (isUrlOrEmailOrNumericField() && rawText == "।") "." else rawText
            val isSinglePunct = !isUrlOrEmailOrNumericField() && text.length == 1 && text[0] in listOf('।', '॥', '?', '!', ';', ',', '.')
            val isCommittedWordWithPunct = !isUrlOrEmailOrNumericField() && text.length > 1 && text.last() in listOf('।', '॥', '?', '!', ';', ',', '.')
            val isPunctuation = isSinglePunct || isCommittedWordWithPunct
            val finalText = if (isSinglePunct) {
                if (text == ",") {
                    val before = try { ic.getTextBeforeCursor(1, 0)?.toString() } catch (_: Exception) { null }
                    if (before != null && before.isNotEmpty() && before[0].isDigit()) {
                        text
                    } else {
                        "$text "
                    }
                } else if (text == "।") {
                    val before = try { ic.getTextBeforeCursor(4, 0)?.toString() } catch (_: Exception) { null }
                    if (before != null && (before.endsWith("।। ") || before.endsWith("।।"))) {
                        // 3rd dot typed in succession -> convert to ellipsis "… "
                        ic.beginBatchEdit()
                        try {
                            val delLen = if (before.endsWith("।। ")) 3 else 2
                            ic.deleteSurroundingText(delLen, 0)
                            ic.commitText("… ", 1)
                            preeditShadow = ""
                            rawInputBuffer.clear()
                            lastAutoDariCommitTime = 0L
                        } finally {
                            ic.endBatchEdit()
                        }
                        updateAutoCaps()
                        return
                    } else if (before != null && (before.endsWith("। ") || before.endsWith("।"))) {
                        // 2nd dot typed in succession -> convert to Double Dari "।। "
                        ic.beginBatchEdit()
                        try {
                            val delLen = if (before.endsWith("। ")) 2 else 1
                            ic.deleteSurroundingText(delLen, 0)
                            ic.commitText("।। ", 1)
                            preeditShadow = ""
                            rawInputBuffer.clear()
                            lastAutoDariCommitTime = 0L
                        } finally {
                            ic.endBatchEdit()
                        }
                        updateAutoCaps()
                        return
                    } else {
                        "$text "
                    }
                } else {
                    "$text "
                }
            } else if (isCommittedWordWithPunct) {
                "$text "
            } else {
                text
            }

            if (text == "।") {
                lastAutoDariCommitTime = android.os.SystemClock.uptimeMillis()
            } else {
                lastAutoDariCommitTime = 0L
            }

            if (finalText.endsWith(" ")) {
                lastCommitWithTrailingSpaceTime = android.os.SystemClock.uptimeMillis()
            }

            ic.beginBatchEdit()
            try {
                if (isPunctuation) {
                    val before = try { ic.getTextBeforeCursor(1, 0)?.toString() } catch (_: Exception) { null }
                    if (before == " ") {
                        ic.deleteSurroundingText(1, 0)
                    }
                }
                ic.commitText(finalText, 1)
                preeditShadow = ""
                rawInputBuffer.clear()
            } finally {
                ic.endBatchEdit()
            }

            updateAutoCaps()

            // Publish next-word candidates immediately if the engine returned them,
            // so the strip cross-fades instead of collapsing then re-expanding (jitter fix).
            if (result.candidates.isNotEmpty()) {
                publishCandidates(result.candidates)
            } else {
                clearCandidates()
            }
        } ?: run {
            // Probaho 2.0 Micro-Haptics: tactile tick when a Kar auto-promotes to an independent vowel
            if (session.getLayout() == LekhaniLayoutType.PROBAHO &&
                keyToken.length == 1 && keyToken[0] in '\u09BE'..'\u09CC' &&
                result.preedit.isNotEmpty() && result.preedit.last() in '\u0985'..'\u0994'
            ) {
                keyboardView?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            }
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
        ensureCursorInComposingRegion(ic)
        isQuickChipDismissed = true
        // Backspacing into a committed word should show its English preview.
        suppressNextWordAfterCommit = false

        // 0. Frictionless Backspace Undo:
        // If Spacebar just performed an auto-substitution or word transformation,
        // tapping Backspace within 2.5 seconds reverts the commit and restores the raw input / pre-edit!
        val activeUndo = activeUndoInfo
        if (activeUndo != null && !session.isComposing()) {
            val textBefore = try { ic.getTextBeforeCursor(activeUndo.committedText.length + 2, 0)?.toString() } catch (_: Exception) { null }
            if (textBefore != null && textBefore.endsWith(activeUndo.committedText)) {
                ic.beginBatchEdit()
                try {
                    ic.deleteSurroundingText(activeUndo.committedText.length, 0)
                    ic.commitText(activeUndo.originalText, 1)
                } finally {
                    ic.endBatchEdit()
                }
                clearUndo()
                refreshSurroundingContext()
                return
            }
        }

        clearUndo()

        // 1. If text is selected in the target app, delete the selection immediately.
        // Prefer the tracked cursor positions (zero cost) over getSelectedText() which is a
        // synchronous Binder IPC call that can block 20+ ms on React Native / Flutter apps.
        val hasSelection = (currentSelStart != currentSelEnd && currentSelStart >= 0 && currentSelEnd >= 0)
        if (hasSelection) {
            rawInputBuffer.clear()
            if (session.isComposing()) {
                session.reset()
                preeditShadow = ""
            }
            ic.finishComposingText()
            ic.commitText("", 1)
            currentSelStart = -1
            currentSelEnd = -1
            if (session.getLayout() == LekhaniLayoutType.GBOARD) {
                keyboardView?.setGboardKarsActive(false)
            }
            clearCandidates()
            return
        }

        // 2. If composing buffer is non-empty, delegate to Rust engine
        if (session.isComposing()) {
            if (rawInputBuffer.isNotEmpty()) {
                val len = rawInputBuffer.length
                val delCount = if (len >= 2 && Character.isSurrogatePair(rawInputBuffer[len - 2], rawInputBuffer[len - 1])) 2 else 1
                rawInputBuffer.setLength(len - delCount)
            }
            val result = try {
                session.handleBackspace()
            } catch (e: LekhaniException) {
                Log.e(TAG, "handleBackspace error: $e")
                return
            }
            setComposingTextSafe(ic, result.preedit)
            if (result.preedit.isEmpty()) {
                preeditShadow = ""
                rawInputBuffer.clear()
                clearCandidates()
                if (session.getLayout() == LekhaniLayoutType.GBOARD) {
                    keyboardView?.setGboardKarsActive(false)
                }
            } else {
                publishCandidates(result.candidates)
                if (session.getLayout() == LekhaniLayoutType.GBOARD) {
                    updateGboardDynamicRowOnBackspace(result.preedit)
                }
            }
        } else {
            rawInputBuffer.clear()
            val inspected = activeInspectedWord
            if (inspected != null &&
                inspected.charsAfterCursor == 0 &&
                session.getLayout() == LekhaniLayoutType.AVRO &&
                !isCurrentFieldPrivate &&
                keyboardPrefs.avroPhoneticBackspaceReopening &&
                inspected.rawPhonetic.isNotBlank()
            ) {
                val rawToReopen = inspected.rawPhonetic.dropLast(1)
                activeInspectedWord = null
                ic.beginBatchEdit()
                try {
                    if (inspected.charsBeforeCursor > 0) {
                        ic.deleteSurroundingText(inspected.charsBeforeCursor, 0)
                    }
                    rawInputBuffer.clear()
                    session.reset()
                    if (rawToReopen.isNotEmpty()) {
                        rawInputBuffer.append(rawToReopen)
                        val immediateBefore = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null }
                        if (!immediateBefore.isNullOrEmpty()) {
                            session.setContext(immediateBefore)
                        }
                        var result: com.lekhani.android.ffi.TypingResult? = null
                        for (i in 0 until rawInputBuffer.length) {
                            result = session.processKey(rawInputBuffer[i].toString())
                        }
                        if (result != null) {
                            preeditShadow = result.preedit
                            setComposingTextSafe(ic, result.preedit)
                            publishCandidates(result.candidates)
                        }
                    } else {
                        preeditShadow = ""
                        clearCandidates()
                    }
                } finally {
                    ic.endBatchEdit()
                }
                refreshSurroundingContext()
                updateAutoCaps()
                return
            }

            activeInspectedWord = null
            // 3. Notify session to trigger rapid-undo mistake penalization (<1500 ms)
            try {
                session.handleBackspace()
            } catch (_: Exception) {}

            // 4. Script-aware character & emoji backspace (never destroys whole word or duplicates letters)
            handleScriptAwareBackspace(ic)
            if (session.getLayout() == LekhaniLayoutType.GBOARD) {
                val before = try { ic.getTextBeforeCursor(2, 0)?.toString() } catch (_: Exception) { null }
                updateGboardDynamicRowOnBackspace(before)
            }
            val isAvro = session.getLayout() == LekhaniLayoutType.AVRO && !isCurrentFieldPrivate
            if (!isAvro) {
                clearCandidates()
            }
            updateAutoCaps()

            // Update candidate strip with English preview & alternatives for word at cursor in Avro mode
            if (isAvro) {
                updateAvroStripForWordAtCursor(ic)
            }
        }
    }

    /**
     * Deletes the preceding character or surrogate pair (emoji).
     * Preserves script integrity by deleting atomic characters/kars individually
     * (e.g. 'লকে' + backspace leaves 'লক') rather than deleting entire [consonant + vowel kar] clusters.
     */
    private fun handleScriptAwareBackspace(ic: InputConnection) {
        // Smart Reversion: If user typed '.' which auto-converted to '। ',
        // tapping Backspace within 2.5 seconds reverts '। ' back to '.'
        if (lastAutoDariCommitTime > 0 && (android.os.SystemClock.uptimeMillis() - lastAutoDariCommitTime <= 2500)) {
            val textBeforeDari = try { ic.getTextBeforeCursor(3, 0)?.toString() } catch (_: Exception) { null }
            if (textBeforeDari != null && (textBeforeDari.endsWith("। ") || textBeforeDari.endsWith("।"))) {
                val delLen = if (textBeforeDari.endsWith("। ")) 2 else 1
                ic.deleteSurroundingText(delLen, 0)
                ic.commitText(".", 1)
                lastAutoDariCommitTime = 0L
                return
            }
        }
        lastAutoDariCommitTime = 0L

        val textBefore = try {
            ic.getTextBeforeCursor(4, 0)?.toString()
        } catch (_: Exception) {
            null
        }

        if (textBefore.isNullOrEmpty()) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            return
        }

        val len = textBefore.length
        val lastChar = textBefore[len - 1]
        // Delete 2 code units only if the previous character is a UTF-16 surrogate pair (emoji)
        val deleteChars = if (Character.isSurrogate(lastChar) && len >= 2) 2 else 1

        val handled = try {
            ic.deleteSurroundingText(deleteChars, 0)
        } catch (_: Exception) {
            false
        }
        if (!handled) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
    }

    /**
     * Handles the Spacebar key.
     * Commits the current composing buffer (NFC-normalized + space appended).
     */
    fun onSpace() {
        keyboardView?.setGboardKarsActive(false)
        isQuickChipDismissed = true
        val ic = currentInputConnection ?: return

        // 0. Double-tap space shortcut: insert Bengali Dāṛi ("। ") or English period (". ")
        val now = android.os.SystemClock.uptimeMillis()
        if (keyboardPrefs.doubleSpaceDariEnabled && !session.isComposing() && !isUrlOrEmailOrNumericField() && (now - lastSpaceTapTime <= 450)) {
            val textBefore = try { ic.getTextBeforeCursor(6, 0)?.toString() } catch (_: Exception) { null }
            if (textBefore != null && textBefore.endsWith(" ") && textBefore.length >= 2) {
                val prevChar = textBefore[textBefore.length - 2]
                val isPunctuation = prevChar in listOf('।', '॥', '.', '?', '!', ',', ';', ':', '\n', ' ')
                if (!isPunctuation) {
                    val punctuation = if (session.getLayout() == LekhaniLayoutType.ENGLISH) ". " else "। "
                    ic.beginBatchEdit()
                    try {
                        ic.deleteSurroundingText(1, 0)
                        ic.commitText(punctuation, 1)
                    } finally {
                        ic.endBatchEdit()
                    }
                    lastSpaceTapTime = 0L
                    clearUndo()
                    refreshSurroundingContext()
                    if (session.getLayout() == LekhaniLayoutType.ENGLISH) {
                        keyboardView?.setShifted(true)
                    }
                    if (isNumericMode && !isNumericFieldMode) {
                        restoreAlphaKeyboard()
                    }
                    return
                }
            }
        }
        lastSpaceTapTime = now

        if (activeInspectedWord != null && !session.isComposing()) {
            activeInspectedWord = null
            clearCandidates()
        }

        val candState = _candidateState.value as? CandidateStripState.Candidates
        val activePrimary = candState?.items?.firstOrNull { it.isPrimary }?.text

        clearUndo()
        ensureCursorInComposingRegion(ic)

        val originalRaw = rawInputBuffer.toString()
        val preeditBeforeSpace = preeditShadow
        rawInputBuffer.clear()

        val isPhonetic = session.getLayout() == LekhaniLayoutType.AVRO
        val chosenForSpace = if (activePrimary != null && !activePrimary.startsWith("=") && isPhonetic) {
            activePrimary
        } else {
            null
        }

        val result = try {
            if (chosenForSpace != null) {
                session.handleSpaceWithChoice(chosenForSpace)
            } else {
                session.handleSpace()
            }
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

            val trimmedCommitted = text.trim()
            val isAvro = session.getLayout() == LekhaniLayoutType.AVRO
            if (isAvro && originalRaw.isNotBlank()) {
                avroHistory.record(trimmedCommitted, originalRaw, withSpace = text.endsWith(" "))
            }
            val candidateOriginal = when {
                isAvro -> null // Avro phonetic transliteration to Bengali is normal typing, not an autocorrection mistake
                originalRaw.isNotEmpty() && originalRaw != trimmedCommitted -> originalRaw
                preeditBeforeSpace.isNotEmpty() && preeditBeforeSpace != trimmedCommitted && preeditBeforeSpace != originalRaw -> preeditBeforeSpace
                else -> null
            }
            val undo = if (candidateOriginal != null && candidateOriginal.isNotBlank()) {
                UndoInfo(originalText = candidateOriginal, committedText = text)
            } else null

            activeUndoInfo = undo

            val currentBefore = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null } ?: ""
            lastPredictedContext = currentBefore.trimEnd()
            if (text.endsWith(" ")) {
                lastCommitWithTrailingSpaceTime = android.os.SystemClock.uptimeMillis()
            }

            if (result.candidates.isNotEmpty()) {
                publishCandidates(result.candidates, undo = undo)
            } else if (undo != null) {
                scheduleUndoExpiry()
                _candidateState.value = CandidateStripState.Undo(undo)
                updateCandidatesVisibility()
            } else {
                clearCandidates()
            }
        }

        // After committing a word, refresh surrounding context for AI scorer
        refreshSurroundingContext()
        updateAutoCaps()

        // Auto-return to letters from numbers/symbols on Spacebar (standard Gboard/iOS convention)
        if (isNumericMode && !isNumericFieldMode) {
            restoreAlphaKeyboard()
        }
    }

    /**
     * Commits the pure deterministic Avro transliteration (Slot 2 / Force Avro),
     * bypassing all dictionary overrides, common words, and typo corrections.
     */
    private fun commitForceAvro() {
        val candState = _candidateState.value as? CandidateStripState.Candidates
        // In the Three-Track model: slot 0 = English escape hatch, slot 1 = AI primary, slot 2 = Force Avro def
        val forceCand = candState?.items?.getOrNull(2)?.text
            ?: candState?.items?.getOrNull(1)?.text
            ?: preeditShadow
        if (!forceCand.isNullOrBlank() && !forceCand.startsWith("=")) {
            onCandidateSelected(forceCand)
        } else {
            onSpace()
        }
    }

    /**
     * Handles Glide / Gesture typing swipe completion.
     * Decodes the visited key path through the native Rust engine, commits the top
     * word, and exposes candidate alternatives to the candidate strip.
     */
    fun handleGlideGesture(keys: List<String>) {
        if (keys.isEmpty()) return
        clearUndo()
        rawInputBuffer.clear()
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
        clearUndo()
        val originalRaw = rawInputBuffer.toString()
        rawInputBuffer.clear()
        if (currentMode == InputViewMode.EMOJI_SEARCH) {
            currentInputConnection?.commitText(candidate, 1)
            recentsManager.addRecent(candidate)
            return
        }

        val ic = currentInputConnection ?: return

        // 1. QuickChip quick-paste selection
        if (_candidateState.value is CandidateStripState.QuickChip) {
            ic.commitText(candidate, 1)
            isQuickChipDismissed = true
            clearCandidates()
            refreshSurroundingContext()
            return
        }

        // 2. Inline contextual emoji selection
        if (EmojiData.isEmoji(candidate)) {
            ic.beginBatchEdit()
            try {
                if (session.isComposing()) {
                    session.reset()
                }
                ic.commitText("$candidate ", 1)
                preeditShadow = ""
            } finally {
                ic.endBatchEdit()
            }
            recentsManager.addRecent(candidate)
            clearCandidates()
            refreshSurroundingContext()
            return
        }

        // 3. Math evaluation prefix stripping ("= 750" → "750")
        val actualCandidate = if (candidate.startsWith("= ")) candidate.substring(2) else candidate
        if (session.getLayout() == LekhaniLayoutType.AVRO && originalRaw.isNotBlank()) {
            avroHistory.record(actualCandidate, originalRaw, withSpace = false)
        }

        // 3a. Inspected word replacement (when user tapped candidate strip for a word at cursor)
        val inspected = activeInspectedWord
        if (inspected != null && !session.isComposing()) {
            activeInspectedWord = null
            ic.beginBatchEdit()
            try {
                if (inspected.charsBeforeCursor > 0 || inspected.charsAfterCursor > 0) {
                    ic.deleteSurroundingText(inspected.charsBeforeCursor, inspected.charsAfterCursor)
                }
                val charAfter = try { ic.getTextAfterCursor(1, 0)?.toString()?.firstOrNull() } catch (_: Exception) { null }
                val shouldOmitTrailingSpace = keyboardPrefs.smartPunctuationSpacing && charAfter != null && charAfter in SMART_PUNCTUATION_CHARS
                val textToCommit = if (shouldOmitTrailingSpace || !inspected.hasTrailingSpace) actualCandidate else "$actualCandidate "
                ic.commitText(textToCommit, 1)
                preeditShadow = ""
                if (textToCommit.endsWith(" ")) {
                    lastCommitWithTrailingSpaceTime = android.os.SystemClock.uptimeMillis()
                }
            } finally {
                ic.endBatchEdit()
            }
            if (session.getLayout() == LekhaniLayoutType.AVRO && inspected.rawPhonetic.isNotBlank()) {
                avroHistory.record(actualCandidate, inspected.rawPhonetic, withSpace = inspected.hasTrailingSpace)
            }
            refreshSurroundingContext()
            updateAutoCaps()
            updateAvroStripForWordAtCursor(ic)
            return
        }

        val result = try {
            session.selectCandidate(actualCandidate)
        } catch (e: LekhaniException) {
            Log.e(TAG, "selectCandidate error: $e")
            return
        }

        val charAfter = try { ic.getTextAfterCursor(1, 0)?.toString()?.firstOrNull() } catch (_: Exception) { null }
        val shouldOmitTrailingSpace = keyboardPrefs.smartPunctuationSpacing && charAfter != null && charAfter in SMART_PUNCTUATION_CHARS

        result.commitText?.let { text ->
            val finalText = if (shouldOmitTrailingSpace && text.endsWith(" ")) text.trimEnd() else text
            ic.beginBatchEdit()
            try {
                ic.commitText(finalText, 1)
                preeditShadow = ""
                if (finalText.endsWith(" ")) {
                    lastCommitWithTrailingSpaceTime = android.os.SystemClock.uptimeMillis()
                }
            } finally {
                ic.endBatchEdit()
            }

            val currentBefore = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null } ?: ""
            lastPredictedContext = currentBefore.trimEnd()

            if (result.candidates.isNotEmpty()) {
                publishCandidates(result.candidates)
            } else {
                val nextWords = try {
                    session.predictNextWords(5u)
                } catch (_: Exception) {
                    emptyList()
                }
                if (nextWords.isNotEmpty()) {
                    publishCandidates(nextWords)
                } else {
                    clearCandidates()
                }
            }
        }

        refreshSurroundingContext()
        updateAutoCaps()
    }

    /**
     * Switches the active layout. Persists the choice to Device Protected Storage.
     */
    fun switchLayout(layout: LekhaniLayoutType, persist: Boolean = true) {
        // Finalize and seal any active composing text in the target editor before switching layouts
        // to prevent setComposingText in the new layout from wiping out the previously typed word!
        currentInputConnection?.finishComposingText()
        session.reset()
        preeditShadow = ""
        rawInputBuffer.clear()
        clearCandidates()
        clearUndo()

        isNumericMode = false
        isMoreSymbolsMode = false
        if (layout == LekhaniLayoutType.ENGLISH) {
            isBengaliDigitsMode = false
        }
        session.setLayout(layout)
        emojiSearchSession?.setLayout(layout)
        keyboardView?.setLayout(LayoutRegistry.get(layout), layout, shifted = false)
        keyboardView?.setGboardKarsActive(false)
        if (layout == LekhaniLayoutType.ENGLISH) {
            ensureEnglishDictionaryLoaded()
        }
        if (persist) {
            devicePrefs.edit().putString(PREF_LAYOUT, layout.name).apply()
        }
        refreshSurroundingContext()
        updateAutoCaps()
        Log.i(TAG, "Layout switched to $layout (persist=$persist)")
    }

    private fun updateGboardDynamicRow(keyToken: String) {
        if (session.getLayout() != LekhaniLayoutType.GBOARD) return
        if (keyToken.isEmpty()) return
        val ch = keyToken[0]
        val isConsonant = (ch in '\u0995'..'\u09B9') || (ch in '\u09DC'..'\u09DF') || ch == '\u09CE'
        if (isConsonant) {
            keyboardView?.setGboardKarsActive(true, consonant = keyToken)
        } else {
            keyboardView?.setGboardKarsActive(false, consonant = "")
        }
    }

    private fun updateGboardDynamicRowOnBackspace(trailingText: String?) {
        if (session.getLayout() != LekhaniLayoutType.GBOARD) return
        if (trailingText.isNullOrEmpty()) {
            keyboardView?.setGboardKarsActive(false, consonant = "")
            return
        }
        val lastChar = trailingText.last()
        val isConsonant = (lastChar in '\u0995'..'\u09B9') || (lastChar in '\u09DC'..'\u09DF') || lastChar == '\u09CE'
        if (isConsonant) {
            keyboardView?.setGboardKarsActive(true, consonant = lastChar.toString())
        } else {
            keyboardView?.setGboardKarsActive(false, consonant = "")
        }
    }

    private fun ensureEnglishDictionaryLoaded() {
        if (isEnglishDictLoaded) return
        val candidates = listOf(
            File(filesDir, "dictionaries/english_dict.bin"),
            File(applicationContext.filesDir, "dictionaries/english_dict.bin"),
            File(createDeviceProtectedStorageContext().filesDir, "dictionaries/english_dict.bin"),
        )
        for (f in candidates) {
            if (f.exists() && f.length() > 0) {
                val ok = session.loadEnglishDictionary(f.absolutePath)
                if (ok) {
                    isEnglishDictLoaded = true
                    Log.i(TAG, "English dictionary loaded from ${f.absolutePath} (${f.length()} bytes)")
                    return
                }
            }
        }
        Log.w(TAG, "English dictionary file not found or failed to load")
    }

    /**
     * Imports user words from Android's system UserDictionary (e.g. from Gboard/Samsung Keyboard)
     * into AutonomousLearner on first run.
     */
    private fun importSystemUserDictionaryIfNeeded() {
        if (devicePrefs.getBoolean("system_user_dict_imported", false)) return

        try {
            val words = mutableListOf<String>()
            val cursor = contentResolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                null,
                null,
                null
            )
            cursor?.use {
                val colIdx = it.getColumnIndex(UserDictionary.Words.WORD)
                if (colIdx >= 0) {
                    while (it.moveToNext()) {
                        val word = it.getString(colIdx)?.trim()
                        if (!word.isNullOrEmpty()) {
                            words.add(word)
                        }
                    }
                }
            }
            if (words.isNotEmpty()) {
                val count = session.importRawWords(words)
                Log.i(TAG, "Imported $count words from system UserDictionary")
            }
            devicePrefs.edit().putBoolean("system_user_dict_imported", true).apply()
        } catch (e: SecurityException) {
            Log.d(TAG, "UserDictionary permission not granted or unavailable: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not import system UserDictionary: ${e.message}")
        }
    }

    /**
     * Persists user-learned vocabulary, candidate memory, and bigrams to local private storage
     * asynchronously on Dispatchers.IO. Skips I/O if no in-memory mutations occurred.
     */
    fun persistUserLearnedAsync() {
        val file = File(filesDir, "user_learned.bin")
        serviceScope.launch(Dispatchers.IO) {
            persistUserLearnedInternal(file)
        }
    }

    private fun persistUserLearnedInternal(file: File) {
        try {
            if (session.isUserLearnedDirty()) {
                session.saveUserLearned(file.absolutePath)
                Log.i(TAG, "User learned data successfully persisted to ${file.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist user learned data: ${e.message}")
        }
    }

    private fun clearUndo() {
        undoDismissJob?.cancel()
        undoDismissJob = null
        activeUndoInfo = null
        val cur = _candidateState.value
        if (cur is CandidateStripState.Undo) {
            _candidateState.value = CandidateStripState.Empty
        } else if (cur is CandidateStripState.Candidates && cur.undoInfo != null) {
            _candidateState.value = CandidateStripState.Candidates(cur.items, undoInfo = null)
        }
    }

    private fun scheduleUndoExpiry() {
        undoDismissJob?.cancel()
        undoDismissJob = serviceScope.launch {
            kotlinx.coroutines.delay(3500)
            withContext(Dispatchers.Main) {
                clearUndo()
            }
        }
    }

    fun onUndoCommit(undoInfo: UndoInfo) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        try {
            ic.deleteSurroundingText(undoInfo.committedText.length, 0)
            ic.commitText(undoInfo.originalText, 1)
            preeditShadow = ""
            rawInputBuffer.clear()
        } finally {
            ic.endBatchEdit()
        }
        try {
            session.penalizeCommit(undoInfo.committedText.trim())
        } catch (e: Exception) {
            Log.w(TAG, "penalizeCommit error: $e")
        }
        clearUndo()
        refreshSurroundingContext()
    }

    /**
     * Publishes candidate list to the Compose candidate strip.
     * Filters out user-blacklisted words, injects smart code tokens, math answers,
     * dynamic date/time, and inline contextual emojis.
     */
    private fun publishCandidates(raw: List<String>, undo: UndoInfo? = null) {
        if (currentMode == InputViewMode.EMOJI_SEARCH || isCurrentFieldPrivate) return
        if (undo != null) {
            activeUndoInfo = undo
            scheduleUndoExpiry()
        }
        val filtered = raw.filter { !blacklist.isBlacklisted(it) }.distinct().toMutableList()

        // 1. Avro Verbatim Token & Code Shield:
        // Configurable positioning: Bengali First (Recommended) vs English First (Classic)
        val rawInput = rawInputBuffer.toString()
        var primaryIndex = 0
        var verbatimIndex = -1
        if (session.getLayout() == LekhaniLayoutType.AVRO && rawInput.isNotBlank()) {
            if (keyboardPrefs.avroShowEnglishPreview) {
                filtered.remove(rawInput)
                val isCode = keyboardPrefs.codeShieldEnabled && SmartAssistant.isCodeToken(rawInput)
                val isBengaliFirst = keyboardPrefs.avroStripOrder == KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST

                if (isCode) {
                    filtered.add(0, rawInput)
                    primaryIndex = 0
                    verbatimIndex = 0
                } else if (isBengaliFirst) {
                    // Bengali First (Recommended / Default):
                    // Slot 0: Primary Bengali candidate (from Rust engine)
                    // Slot 1: Verbatim English token
                    primaryIndex = 0
                    if (filtered.isNotEmpty()) {
                        filtered.add(1, rawInput)
                        verbatimIndex = 1
                    } else {
                        filtered.add(0, rawInput)
                        verbatimIndex = 0
                    }
                } else {
                    // English First (Classic Avro):
                    // Slot 0: Verbatim English token
                    // Slot 1: Primary Bengali candidate
                    filtered.add(0, rawInput)
                    verbatimIndex = 0
                    primaryIndex = if (filtered.size > 1) 1 else 0
                }
            } else {
                primaryIndex = 0
            }
        }

        // 2. Smart Math Evaluation: e.g. "500+250=" -> "= 750"
        val mathAnswer = SmartAssistant.evaluateMath(rawInput)
        if (mathAnswer != null && !filtered.contains("= $mathAnswer") && !filtered.contains(mathAnswer)) {
            filtered.add(0, "= $mathAnswer")
            primaryIndex = 0
        }

        // 3. Dynamic Date & Time Suggestions
        val isEng = session.getLayout() == LekhaniLayoutType.ENGLISH
        val topCandidate = filtered.getOrNull(primaryIndex) ?: filtered.firstOrNull() ?: ""
        if (rawInput.length >= 3 || topCandidate.length >= 3) {
            if (SmartAssistant.isDateQuery(rawInput) || SmartAssistant.isDateQuery(topCandidate)) {
                val dateStr = SmartAssistant.getFormattedDate(!isEng)
                if (!filtered.contains(dateStr)) filtered.add(dateStr)
            }
            if (SmartAssistant.isTimeQuery(rawInput) || SmartAssistant.isTimeQuery(topCandidate)) {
                val timeStr = SmartAssistant.getFormattedTime(!isEng)
                if (!filtered.contains(timeStr)) filtered.add(timeStr)
            }
        }

        // 4. Inline Contextual Emojis: Gboard-style trailing suggestions (only when token length >= 2)
        if (rawInput.length >= 2 || topCandidate.length >= 2) {
            val contextualEmojis = mutableListOf<String>()
            if (rawInput.length >= 2) {
                contextualEmojis.addAll(EmojiData.findContextualEmojis(rawInput, 2))
            }
            if (topCandidate.length >= 2 && contextualEmojis.size < 2) {
                contextualEmojis.addAll(EmojiData.findContextualEmojis(topCandidate, 2 - contextualEmojis.size))
            }
            for (emoji in contextualEmojis.distinct()) {
                if (!filtered.contains(emoji)) {
                    filtered.add(emoji)
                }
            }
        }

        val newState = if (filtered.isEmpty()) {
            if (undo != null) CandidateStripState.Undo(undo) else CandidateStripState.Empty
        } else {
            CandidateStripState.Candidates(
                HomophoneAnnotator.annotate(filtered, primaryIdx = primaryIndex, verbatimIdx = verbatimIndex),
                undoInfo = undo
            )
        }
        // Skip redundant emission: if candidates haven't changed, don't trigger
        // a Compose recomposition — this is the primary cause of the per-keystroke jitter.
        val cur = _candidateState.value
        if (newState is CandidateStripState.Candidates && cur is CandidateStripState.Candidates
            && newState.items == cur.items
            && newState.undoInfo == cur.undoInfo) {
            return
        }
        _candidateState.value = newState
        updateCandidatesVisibility()
    }

    /**
     * Clears all candidates and hides the candidate strip view.
     */
    private fun clearCandidates() {
        if (currentMode == InputViewMode.EMOJI_SEARCH) return
        activeInspectedWord = null
        _candidateState.value = CandidateStripState.Empty
        updateCandidatesVisibility()
        checkAndShowQuickChip()
    }

    private fun handleToolbarToolClick(tool: KeyboardPreferences.ToolbarTool) {
        when (tool) {
            KeyboardPreferences.ToolbarTool.EMOJI -> {
                setInputViewMode(InputViewMode.EMOJI)
            }
            KeyboardPreferences.ToolbarTool.TEXT_EDITOR -> {
                setInputViewMode(InputViewMode.TEXT_EDITOR)
            }
            KeyboardPreferences.ToolbarTool.VOICE -> {
                if (!audioManager.hasRecordPermission()) {
                    val msg = if (keyboardPrefs.uiLanguage == "en") "Microphone permission required. Please enable in App Settings." else "মাইক্রোফোন পারমিশন প্রয়োজন। দয়া করে সেটিংসে চালু করুন।"
                    showNotice(msg, Icons.Filled.Mic)
                    return
                }
                
                if (audioManager.voiceState.value is VoiceTypingState.Listening) {
                    val result = audioManager.stopStreaming()
                    if (result.isNotBlank()) {
                        currentInputConnection?.finishComposingText()
                        currentInputConnection?.commitText(result, 1)
                        refreshSurroundingContext()
                    }
                } else {
                    audioManager.startStreaming { finalResult ->
                        if (finalResult.isNotBlank()) {
                            currentInputConnection?.finishComposingText()
                            currentInputConnection?.commitText(finalResult, 1)
                            refreshSurroundingContext()
                        }
                    }
                }
            }
            KeyboardPreferences.ToolbarTool.CLIPBOARD -> {
                setInputViewMode(InputViewMode.CLIPBOARD)
            }
            KeyboardPreferences.ToolbarTool.NUMPAD -> {
                if (isNumericFieldMode || isPhoneDialpadMode) {
                    restoreAlphaKeyboard()
                } else {
                    if (previousLayoutBeforeNumeric == null) {
                        previousLayoutBeforeNumeric = session.getLayout()
                    }
                    isNumericFieldMode = true
                    isPhoneDialpadMode = false
                    isNumericMode = true
                    isMoreSymbolsMode = false
                    setInputViewMode(InputViewMode.KEYBOARD)
                    updateNumberSymbolsKeyboard()
                }
            }
            KeyboardPreferences.ToolbarTool.RESIZE -> {
                setInputViewMode(InputViewMode.RESIZE)
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
        if (session.isComposing() || preeditShadow.isNotEmpty()) {
            ic.finishComposingText()
            session.reset()
            preeditShadow = ""
            rawInputBuffer.clear()
            clearCandidates()
            clearUndo()
        }
        if (deltaChars < 0) {
            repeat(-deltaChars) {
                ic.sendKeyEvent(curLeftDown)
                ic.sendKeyEvent(curLeftUp)
            }
        } else if (deltaChars > 0) {
            repeat(deltaChars) {
                ic.sendKeyEvent(curRightDown)
                ic.sendKeyEvent(curRightUp)
            }
        }
    }

    private fun handleSwipeDeletePreview(wordCount: Int) {
        val ic = currentInputConnection
        if (wordCount <= 0) {
            if (isSwipeDeleteActive) {
                if (swipeDeleteAnchorCursor >= 0 && keyboardPrefs.swipeDeleteHighlightInApp) {
                    ic?.setSelection(swipeDeleteAnchorCursor, swipeDeleteAnchorCursor)
                }
                isSwipeDeleteActive = false
                swipeDeleteAnchorCursor = -1
                activeSwipeDeletePreviewText = ""
                activeSwipeSnapshotText = ""
            }
            restoreStateAfterSwipeDeleteCancel()
            updateCandidatesVisibility()
            return
        }

        if (!isSwipeDeleteActive) {
            isSwipeDeleteActive = true
            swipeDeleteAnchorCursor = if (currentSelEnd >= 0) {
                currentSelEnd
            } else {
                try {
                    val extracted = ic?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                    if (extracted != null && extracted.selectionEnd >= 0) {
                        extracted.selectionEnd
                    } else {
                        -1
                    }
                } catch (_: Exception) {
                    -1
                }
            }
            // Capture fresh snapshot directly from InputConnection at the very start of the gesture
            activeSwipeSnapshotText = try {
                ic?.getTextBeforeCursor(1024, 0)?.toString() ?: ""
            } catch (_: Exception) {
                ""
            }
            if (activeSwipeSnapshotText.isEmpty() && preeditShadow.isEmpty()) {
                isSwipeDeleteActive = false
                swipeDeleteAnchorCursor = -1
                activeSwipeDeletePreviewText = ""
                activeSwipeSnapshotText = ""
                keyboardView?.cancelSwipeDelete()
                restoreStateAfterSwipeDeleteCancel()
                updateCandidatesVisibility()
                return
            }
        }

        val previewText = computeSwipeDeletePreviewText(wordCount)
        if (previewText.isEmpty()) {
            if (swipeDeleteAnchorCursor >= 0 && keyboardPrefs.swipeDeleteHighlightInApp) {
                ic?.setSelection(swipeDeleteAnchorCursor, swipeDeleteAnchorCursor)
            }
            isSwipeDeleteActive = false
            swipeDeleteAnchorCursor = -1
            activeSwipeDeletePreviewText = ""
            activeSwipeSnapshotText = ""
            keyboardView?.cancelSwipeDelete()
            restoreStateAfterSwipeDeleteCancel()
            updateCandidatesVisibility()
            return
        }

        activeSwipeDeletePreviewText = previewText

        // Highlight real text in target application
        if (keyboardPrefs.swipeDeleteHighlightInApp && swipeDeleteAnchorCursor >= 0) {
            val selStart = maxOf(0, swipeDeleteAnchorCursor - previewText.length)
            ic?.setSelection(selStart, swipeDeleteAnchorCursor)
        }

        _candidateState.value = CandidateStripState.SwipeDeletePreview(
            previewText = previewText,
            wordCount = wordCount,
            granularity = DeleteGranularity.WORD,
            onCopy = {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Lekhani", previewText))
                keyboardView?.let { feedbackManager.onKeyFeedback(it) }
                if (swipeDeleteAnchorCursor >= 0 && keyboardPrefs.swipeDeleteHighlightInApp) {
                    ic?.setSelection(swipeDeleteAnchorCursor, swipeDeleteAnchorCursor)
                }
                isSwipeDeleteActive = false
                swipeDeleteAnchorCursor = -1
                activeSwipeDeletePreviewText = ""
                activeSwipeSnapshotText = ""
                keyboardView?.cancelSwipeDelete()
                restoreStateAfterSwipeDeleteCancel()
                updateCandidatesVisibility()
            },
            onCut = {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                cm?.setPrimaryClip(android.content.ClipData.newPlainText("Lekhani", previewText))
                keyboardView?.let { feedbackManager.onKeyFeedback(it) }
                keyboardView?.cancelSwipeDelete()
                handleSwipeDelete(wordCount)
            }
        )
        candidateStripComposeView?.visibility = View.VISIBLE
    }

    private fun restoreStateAfterSwipeDeleteCancel() {
        if (preeditShadow.isNotEmpty()) {
            publishCandidates(listOf(preeditShadow))
        } else {
            _candidateState.value = CandidateStripState.Empty
            checkAndShowQuickChip()
            refreshSurroundingContext()
        }
    }

    private fun computeSwipeDeletePreviewText(wordCount: Int): String {
        if (wordCount <= 0) return ""
        var remainingWords = wordCount
        val buffer = StringBuilder()
        if (preeditShadow.isNotEmpty()) {
            buffer.append(preeditShadow)
            remainingWords--
        }
        if (remainingWords > 0) {
            val before = activeSwipeSnapshotText
            if (before.isNotEmpty()) {
                var charsToDelete = 0
                var wordsFound = 0
                var inWord = false
                for (i in before.length - 1 downTo 0) {
                    val ch = before[i]
                    if (!ch.isWhitespace()) {
                        inWord = true
                    } else if (inWord) {
                        wordsFound++
                        inWord = false
                        if (wordsFound >= remainingWords) break
                    }
                    charsToDelete++
                }
                if (charsToDelete > 0) {
                    val committed = before.takeLast(charsToDelete)
                    if (buffer.isNotEmpty()) {
                        buffer.insert(0, committed)
                    } else {
                        buffer.append(committed)
                    }
                }
            }
        }
        return buffer.toString()
    }

    private fun handleSwipeDelete(wordCount: Int) {
        if (wordCount <= 0) return
        val ic = currentInputConnection ?: return

        val previewToDelete = activeSwipeDeletePreviewText.ifEmpty {
            computeSwipeDeletePreviewText(wordCount)
        }
        activeSwipeDeletePreviewText = ""
        activeSwipeSnapshotText = ""

        val anchorCursor = swipeDeleteAnchorCursor
        val wasHighlightingInApp = isSwipeDeleteActive && keyboardPrefs.swipeDeleteHighlightInApp && anchorCursor >= 0
        isSwipeDeleteActive = false
        swipeDeleteAnchorCursor = -1

        if (previewToDelete.isEmpty()) {
            return
        }

        ic.beginBatchEdit()
        try {
            // 1. If currently composing Bengali in session, discard composition first
            if (preeditShadow.isNotEmpty()) {
                session.reset()
                preeditShadow = ""
                ic.setComposingText("", 0)
                clearCandidates()
            }

            // 2. If text was highlighted in app, commit empty string directly to delete selection cleanly
            if (wasHighlightingInApp) {
                ic.commitText("", 0)
            } else {
                ic.deleteSurroundingText(previewToDelete.length, 0)
            }
        } finally {
            ic.endBatchEdit()
        }

        // 3. Create and publish Undo state
        val undo = UndoInfo(originalText = previewToDelete, committedText = "")
        activeUndoInfo = undo
        scheduleUndoExpiry()
        _candidateState.value = CandidateStripState.Undo(undo)
        candidateStripComposeView?.visibility = View.VISIBLE

        refreshSurroundingContext()
    }

    fun setFormFactor(newForm: KeyboardPreferences.FormFactor) {
        keyboardPrefs.formFactor = newForm
        applyFloatingCardLayout(newForm)
        keyboardView?.applyPreferences(keyboardPrefs, feedbackManager)
        keyboardView?.requestLayout()
        keyboardView?.invalidate()
    }

    private fun updateParentLayoutHierarchy(isFloating: Boolean) {
        val root = rootInputContainer ?: return
        val win = window?.window

        if (isFloating) {
            win?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win?.setGravity(Gravity.TOP or Gravity.START)
            win?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            root.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        } else {
            win?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            win?.setGravity(Gravity.BOTTOM)
            root.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        var p = root.parent
        while (p is ViewGroup) {
            p.clipChildren = false
            p.clipToPadding = false
            val lp = p.layoutParams
            if (lp != null) {
                val targetH = if (isFloating) {
                    ViewGroup.LayoutParams.MATCH_PARENT
                } else {
                    ViewGroup.LayoutParams.WRAP_CONTENT
                }
                if (lp.height != targetH) {
                    lp.height = targetH
                    p.layoutParams = lp
                }
            }
            if (isFloating) {
                p.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            p = p.parent
        }
    }

    private fun requestInsetsRecalculation() {
        if (isInputViewShown) {
            val win = window?.window
            win?.decorView?.let { decor ->
                decor.requestApplyInsets()
                decor.requestLayout()
            }
        }
    }

    private fun applyFloatingCardLayout(formFactor: KeyboardPreferences.FormFactor) {
        val card = floatingCardContainer ?: return
        val root = rootInputContainer ?: return
        val density = resources.displayMetrics.density
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels
        val activeTheme = ThemeRegistry.resolveTheme(this, keyboardPrefs.themeId)

        if (formFactor == KeyboardPreferences.FormFactor.FLOATING) {
            floatingHeaderComposeView?.visibility = View.VISIBLE
            floatingFooterComposeView?.visibility = View.VISIBLE
            root.setBackgroundColor(android.graphics.Color.TRANSPARENT)

            val floatingW = (displayWidth * keyboardPrefs.floatingWidthPercent).toInt()
                .coerceIn((270 * density).toInt(), (displayWidth - 16 * density).toInt().coerceAtLeast((270 * density).toInt()))

            card.layoutParams = FrameLayout.LayoutParams(floatingW, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            val cornerRadiusPx = 16f * density
            val shapeDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusPx
                setColor(activeTheme.backgroundColor)
                setStroke((1f * density).toInt(), androidx.core.graphics.ColorUtils.setAlphaComponent(activeTheme.labelColor, 0x33))
            }
            card.background = shapeDrawable
            card.clipToOutline = true
            card.elevation = 16f * density

            val floatingH = card.height.takeIf { it > 0 } ?: (280f * density).toInt()

            val minX = 8f * density
            val maxX = (displayWidth - floatingW - 8f * density).coerceAtLeast(minX)
            val minY = 48f * density
            val maxY = (displayHeight - floatingH - 24f * density).coerceAtLeast(minY)

            val defaultX = ((displayWidth - floatingW) / 2f).coerceIn(minX, maxX)
            val defaultY = (displayHeight - floatingH - 80f * density).coerceIn(minY, maxY)

            val savedX = keyboardPrefs.floatingOffsetX
            val savedY = keyboardPrefs.floatingOffsetY

            val targetX = if (savedX < minX || savedX > maxX) defaultX else savedX
            val targetY = if (savedY < minY || savedY > maxY) defaultY else savedY

            card.translationX = targetX
            card.translationY = targetY

            updateParentLayoutHierarchy(isFloating = true)
        } else {
            floatingHeaderComposeView?.visibility = View.GONE
            floatingFooterComposeView?.visibility = View.GONE
            root.setBackgroundColor(activeTheme.backgroundColor)

            card.layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM
            }
            card.background = null
            card.clipToOutline = false
            card.elevation = 0f
            card.translationX = 0f
            card.translationY = 0f

            updateParentLayoutHierarchy(isFloating = false)
        }
        card.requestLayout()
        root.requestLayout()
        requestInsetsRecalculation()
    }

    private fun handleFloatingDrag(dx: Float, dy: Float) {
        val card = floatingCardContainer ?: return
        val displayWidth = resources.displayMetrics.widthPixels
        val displayHeight = resources.displayMetrics.heightPixels
        val density = resources.displayMetrics.density
        val floatingW = card.width.takeIf { it > 0 } ?: (displayWidth * keyboardPrefs.floatingWidthPercent).toInt()
        val floatingH = card.height.takeIf { it > 0 } ?: (280f * density).toInt()

        val minX = 8f * density
        val maxX = (displayWidth - floatingW - 8f * density).coerceAtLeast(minX)
        val minY = 48f * density
        val maxY = (displayHeight - floatingH - 24f * density).coerceAtLeast(minY)

        val newX = (card.translationX + dx).coerceIn(minX, maxX)
        val newY = (card.translationY + dy).coerceIn(minY, maxY)

        card.translationX = newX
        card.translationY = newY

        requestInsetsRecalculation()
    }

    private fun saveFloatingPosition() {
        val card = floatingCardContainer ?: return
        if (card.translationX > 0f && card.translationY > 0f) {
            keyboardPrefs.floatingOffsetX = card.translationX
            keyboardPrefs.floatingOffsetY = card.translationY
        }
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        if (keyboardPrefs.formFactor == KeyboardPreferences.FormFactor.FLOATING) {
            val card = floatingCardContainer
            val decorView = window?.window?.decorView
            val decorH = decorView?.height ?: resources.displayMetrics.heightPixels
            val decorW = decorView?.width ?: resources.displayMetrics.widthPixels

            outInsets.contentTopInsets = decorH
            outInsets.visibleTopInsets = decorH

            if (card != null && card.isShown && card.width > 0 && card.height > 0) {
                val loc = IntArray(2)
                card.getLocationInWindow(loc)
                val left = loc[0].coerceAtLeast(0)
                val top = loc[1].coerceAtLeast(0)
                val right = (left + card.width).coerceAtMost(decorW)
                val bottom = (top + card.height).coerceAtMost(decorH)

                if (right > left && bottom > top) {
                    outInsets.touchableRegion.set(left, top, right, bottom)
                } else {
                    outInsets.touchableRegion.setEmpty()
                }
                outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            } else {
                outInsets.touchableRegion.setEmpty()
                outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
            }
        }
    }

    private fun cycleTheme() {
        val themeIds = ThemeRegistry.QUICK_TOOLBAR_THEME_IDS
        val curId = keyboardPrefs.themeId
        val idx = themeIds.indexOf(curId)
        val nextId = if (idx == -1) themeIds.first() else themeIds[(idx + 1) % themeIds.size]
        keyboardPrefs.themeId = nextId
        val nextTheme = ThemeRegistry.resolveTheme(this, nextId)
        _themeFlow.value = nextTheme
        applyFloatingCardLayout(keyboardPrefs.formFactor)
        keyboardView?.applyTheme(nextTheme)
        keyboardView?.let { feedbackManager.onKeyFeedback(it) }
    }

    /**
     * Enforces password / incognito field policy.
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
        val noPersonalizedLearning = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0

        val isTextPassword = inputClass == InputType.TYPE_CLASS_TEXT && (
            inputVariation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            inputVariation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )
        val isNumericPassword = inputClass == InputType.TYPE_CLASS_NUMBER && (
            inputVariation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        )
        val isPassword = isTextPassword || isNumericPassword
        // isPrivate: hides ALL suggestions and clipboard pills ONLY for password fields
        val isPrivate = isPassword
        // isLearningFrozen: stops auto-learning and AI context updates in private/password,
        // no-suggestions, and explicit incognito fields (Chrome, Telegram, etc.)
        val isLearningFrozen = isPassword || noSuggestions || noPersonalizedLearning

        isCurrentFieldPrivate = isPrivate
        session.setPrivateField(isLearningFrozen)

        if (isPrivate) {
            clearCandidates()
        }

        if (isTextPassword) {
            // Auto-switch to English QWERTY for text passwords and preserve the previous layout
            if (previousLayoutBeforePassword == null && session.getLayout() != LekhaniLayoutType.ENGLISH) {
                previousLayoutBeforePassword = session.getLayout()
            }
            if (session.getLayout() != LekhaniLayoutType.ENGLISH) {
                switchLayout(LekhaniLayoutType.ENGLISH, persist = false)
            }
        } else {
            // Restore previous layout if returning from a password field
            previousLayoutBeforePassword?.let { restoreLayout ->
                previousLayoutBeforePassword = null
                restoreAlphaKeyboard()
                if (session.getLayout() != restoreLayout) {
                    switchLayout(restoreLayout, persist = false)
                }
            }
        }

        val isPhone = inputClass == InputType.TYPE_CLASS_PHONE
        val isNumeric = inputClass == InputType.TYPE_CLASS_NUMBER || inputClass == InputType.TYPE_CLASS_DATETIME

        val shouldAutoSwitchNumpad = keyboardPrefs.autoSwitchNumpad && (isPhone || isNumeric)

        if (shouldAutoSwitchNumpad && isPhone) {
            isPhoneDialpadMode = true
            isNumericFieldMode = false
            isNumericMode = true
            isMoreSymbolsMode = false
            if (previousLayoutBeforeNumeric == null) {
                previousLayoutBeforeNumeric = session.getLayout()
            }
            updateNumberSymbolsKeyboard()
        } else if (shouldAutoSwitchNumpad && isNumeric) {
            isNumericFieldMode = true
            isPhoneDialpadMode = false
            isNumericMode = true
            isMoreSymbolsMode = false
            if (previousLayoutBeforeNumeric == null) {
                previousLayoutBeforeNumeric = session.getLayout()
            }
            updateNumberSymbolsKeyboard()
        } else {
            if (isPhoneDialpadMode || isNumericFieldMode || isNumericMode || isMoreSymbolsMode) {
                restoreAlphaKeyboard()
            }
        }
    }

    private fun updateEnterActionAndFieldType(info: EditorInfo?) {
        val kv = keyboardView ?: return
        val actionIcon = EnterKeyResolver.determineActionIcon(info, kv.isShiftActive)
        val fieldType = EnterKeyResolver.determineFieldType(info)
        kv.setEnterActionIcon(actionIcon)
        kv.setFieldType(fieldType)
    }

    fun isUrlOrEmailOrNumericField(): Boolean {
        if (isNumericMode || isNumericFieldMode || isPhoneDialpadMode) return true
        val info = currentInputEditorInfo ?: return false
        val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_CLASS_NUMBER ||
            inputClass == InputType.TYPE_CLASS_PHONE ||
            inputClass == InputType.TYPE_CLASS_DATETIME) {
            return true
        }
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_URI ||
               variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
               variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
               variation == InputType.TYPE_TEXT_VARIATION_FILTER
    }

    /**
     * Dynamically updates the Shift state for the English layout based on cursor position,
     * sentence boundaries (including Bengali Dari '।'), and editor capitalization flags.
     */
    fun updateAutoCaps() {
        if (session.getLayout() != LekhaniLayoutType.ENGLISH) return
        val kv = keyboardView ?: return
        if (kv.isShiftLocked) return
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass != InputType.TYPE_CLASS_TEXT) {
            kv.setShifted(false)
            return
        }

        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_URI ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
            kv.setShifted(false)
            return
        }

        var reqModes = 0
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS) != 0) {
            reqModes = reqModes or android.text.TextUtils.CAP_MODE_CHARACTERS
        }
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS) != 0) {
            reqModes = reqModes or android.text.TextUtils.CAP_MODE_WORDS
        }
        if ((inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES) != 0) {
            reqModes = reqModes or android.text.TextUtils.CAP_MODE_SENTENCES
        }
        if (reqModes == 0) {
            reqModes = android.text.TextUtils.CAP_MODE_SENTENCES
        }

        var shouldShift = try {
            ic.getCursorCapsMode(reqModes) != 0
        } catch (_: Exception) {
            false
        }

        // Support Bengali Dari ('।') as sentence terminator for English
        if (!shouldShift) {
            val textBefore = try { ic.getTextBeforeCursor(4, 0)?.toString() } catch (_: Exception) { null }
            if (textBefore != null && (textBefore.endsWith("। ") || textBefore.endsWith("।\n") || textBefore == "।")) {
                shouldShift = true
            }
        }

        kv.setShifted(shouldShift)
    }

    /**
     * Refreshes the surrounding text context in the Rust AI scorer.
     *
     * Run on a background coroutine (Dispatchers.IO for the Binder IPC call to
     * getTextBeforeCursor, then switch to Default for the Rust call) so the
     * main thread is never blocked.
     */
    /**
     * Shows a brief in-strip notification or error banner.
     */
    private fun showNotice(message: String, icon: ImageVector? = Icons.Filled.Warning, durationMs: Long = 3500L) {
        _candidateState.value = CandidateStripState.Notice(
            message = message,
            icon = icon,
            onDismiss = {
                _candidateState.value = CandidateStripState.Empty
                updateCandidatesVisibility()
            }
        )
        updateCandidatesVisibility()
        serviceScope.launch {
            kotlinx.coroutines.delay(durationMs)
            if (_candidateState.value is CandidateStripState.Notice) {
                _candidateState.value = CandidateStripState.Empty
                updateCandidatesVisibility()
            }
        }
    }

    private fun refreshSurroundingContext() {
        if (isCurrentFieldPrivate || (currentMode != InputViewMode.KEYBOARD && currentMode != InputViewMode.EMOJI_SEARCH)) {
            if (isCurrentFieldPrivate) {
                clearCandidates()
            }
            return
        }
        refreshContextJob?.cancel()
        refreshContextJob = serviceScope.launch {
            kotlinx.coroutines.delay(120L)
            if (isCurrentFieldPrivate || (currentMode != InputViewMode.KEYBOARD && currentMode != InputViewMode.EMOJI_SEARCH)) return@launch

            val (contextText, afterText) = withContext(Dispatchers.IO) {
                try {
                    kotlinx.coroutines.withTimeout(500L) {
                        val ic = currentInputConnection
                        val before = ic?.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() ?: ""
                        val after = ic?.getTextAfterCursor(64, 0)?.toString() ?: ""
                        Pair(before, after)
                    }
                } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                    // Target app is unresponsive (e.g. Chromium WebView under heavy load);
                    // skip context update rather than blocking the IO dispatcher indefinitely.
                    Pair("", "")
                }
            }
            cachedSurroundingContext = contextText
            val effectiveContext = contextText
            withContext(Dispatchers.Default) {
                // Only overwrite session context if IPC returned genuine text;
                // never erase valid shadow context accumulated from committed words.
                if (contextText.isNotEmpty()) {
                    session.setContext(contextText)
                }
                session.setRightContext(afterText)
                // Asynchronous background next-word prediction: keep UI thread 120 FPS.
                // suppressNextWordAfterCommit is set when a word is committed via space/tap.
                // It is cleared on the first keystroke of the next word, preventing the
                // "post-space flash" where predictions briefly overwrite the empty strip.
                if (!isCurrentFieldPrivate && preeditShadow.isEmpty() && rawInputBuffer.isEmpty() &&
                    effectiveContext.isNotBlank() && !suppressNextWordAfterCommit) {
                    val nextWords = try {
                        session.predictNextWords(5u)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (nextWords.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (!isCurrentFieldPrivate && preeditShadow.isEmpty() && rawInputBuffer.isEmpty() &&
                                activeInspectedWord == null && !suppressNextWordAfterCommit) {
                                if (_candidateState.value !is CandidateStripState.Undo) {
                                    val curCandidates = (_candidateState.value as? CandidateStripState.Candidates)?.items
                                    val trimmedCtx = effectiveContext.trimEnd()
                                    // If strip already displays predictions with the exact same top candidate,
                                    // or if predictions were already published for this context, avoid redundant recomposition churn
                                    if (trimmedCtx != lastPredictedContext && (curCandidates.isNullOrEmpty() || curCandidates.firstOrNull()?.text != nextWords.firstOrNull())) {
                                        lastPredictedContext = trimmedCtx
                                        publishCandidates(nextWords)
                                    }
                                }
                            }
                        }
                    }
                }
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

    // ── Avro Dynamic Word Recomposition ────────────────────────────────────────

    private fun isWordChar(c: Char): Boolean {
        if (c.isWhitespace()) return false
        if (c == '।' || c == '॥' || c == ',' || c == '.' || c == '?' || c == '!' ||
            c == ';' || c == ':' || c == '"' || c == '\'' || c == '(' || c == ')' ||
            c == '[' || c == ']' || c == '{' || c == '}' || c == '<' || c == '>' ||
            c == '/' || c == '\\' || c == '@' || c == '#' || c == '$' || c == '%' ||
            c == '^' || c == '&' || c == '*' || c == '+' || c == '=' || c == '~' || c == '`') {
            return false
        }
        return true
    }

    // ── Avro Dynamic Word Inspection (Gboard-Style) ─────────────────────────────

    /**
     * Ephemeral inspection state for a word at or around the cursor in Avro phonetic mode.
     * When the user taps into a word, navigates, or backspaces on committed text,
     * the word is inspected without modifying the document, displaying the English
     * transliteration preview and candidate alternatives on the candidate strip.
     *
     * @param word The full word token around the cursor
     * @param charsBeforeCursor Number of characters of the word before the cursor
     * @param charsAfterCursor Number of characters of the word after the cursor
     * @param rawEnglish The canonical Avro English phonetic representation
     */
    private data class InspectedWord(
        val word: String,
        val charsBeforeCursor: Int,
        val charsAfterCursor: Int,
        val rawPhonetic: String,
        val previewEnglish: String,
        val hasTrailingSpace: Boolean = false,
    ) {
        val rawEnglish: String get() = rawPhonetic
    }

    /**
     * Inspects the word under or adjacent to the cursor in Avro phonetic mode
     * without mutating or destroying document text.
     *
     * Displays the English phonetic preview in Slot 0 (e.g. "sonar") and the
     * canonical Bengali word in Slot 1 (e.g. "সোনার"), followed by alternative
     * candidate suggestions.
     *
     * If the user taps a suggestion on the strip, [onCandidateSelected] cleanly
     * replaces the inspected word. If the user continues typing or moves away,
     * the document remains pristine.
     */
    private fun updateAvroStripForWordAtCursor(ic: InputConnection) {
        if (currentMode != InputViewMode.KEYBOARD) return
        if (session.getLayout() != LekhaniLayoutType.AVRO) return
        if (isCurrentFieldPrivate) return
        if (session.isComposing() || preeditShadow.isNotEmpty()) return

        val curState = _candidateState.value
        if (curState is CandidateStripState.Selection ||
            curState is CandidateStripState.SwipeDeletePreview ||
            curState is CandidateStripState.EmojiSearch
        ) {
            return
        }

        val before = try { ic.getTextBeforeCursor(CONTEXT_CHAR_LIMIT, 0)?.toString() } catch (_: Exception) { null } ?: ""
        val after = try { ic.getTextAfterCursor(64, 0)?.toString() } catch (_: Exception) { null } ?: ""

        // Backward scan in before to find the start of the current word
        var beforeWordCount = 0
        var i = before.length - 1
        while (i >= 0 && isWordChar(before[i])) {
            beforeWordCount++
            i--
        }

        // Forward scan in after to find the end of the current word
        var afterWordCount = 0
        var j = 0
        while (j < after.length && isWordChar(after[j])) {
            afterWordCount++
            j++
        }

        val totalWordLen = beforeWordCount + afterWordCount

        if (totalWordLen == 0) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit) {
                // Anti-flicker cache: if the strip is already stably showing predictions for this context, do not re-emit
                val curState = _candidateState.value
                if (trimmedBefore == lastPredictedContext && curState is CandidateStripState.Candidates && curState.items.isNotEmpty()) {
                    return
                }
                session.setContext(trimmedBefore)
                val nextWords = try {
                    session.predictNextWords(5u)
                } catch (_: Exception) {
                    emptyList()
                }
                if (nextWords.isNotEmpty()) {
                    lastPredictedContext = trimmedBefore
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    val curItems = (_candidateState.value as? CandidateStripState.Candidates)?.items
                    if (curItems != annotated) {
                        _candidateState.value = CandidateStripState.Candidates(annotated)
                        updateCandidatesVisibility()
                    }
                    return
                }
            }
            if (activeInspectedWord != null || _candidateState.value is CandidateStripState.Candidates) {
                activeInspectedWord = null
                clearCandidates()
            }
            return
        }

        val word = before.substring(before.length - beforeWordCount) + after.substring(0, afterWordCount)
        val isBengali = AvroReverseTransliterator.isBengaliWord(word)
        val isAsciiWord = !isBengali && word.isNotEmpty() && word.all { it in 'a'..'z' || it in 'A'..'Z' }
        if (!isBengali && !isAsciiWord) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit) {
                session.setContext(trimmedBefore)
                val nextWords = try { session.predictNextWords(5u) } catch (_: Exception) { emptyList() }
                if (nextWords.isNotEmpty()) {
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    val curItems = (_candidateState.value as? CandidateStripState.Candidates)?.items
                    if (curItems != annotated) {
                        _candidateState.value = CandidateStripState.Candidates(annotated)
                        updateCandidatesVisibility()
                    }
                    return
                }
            }
            if (activeInspectedWord != null) {
                activeInspectedWord = null
                clearCandidates()
            }
            return
        }

        val rawEnglish = if (isBengali) {
            avroHistory.get(word) ?: AvroReverseTransliterator.bengaliToAvro(word)
        } else {
            word
        }
        if (rawEnglish.isBlank()) {
            val trimmedBefore = before.trimEnd()
            if (trimmedBefore.isNotEmpty() && !suppressNextWordAfterCommit) {
                session.setContext(trimmedBefore)
                val nextWords = try { session.predictNextWords(5u) } catch (_: Exception) { emptyList() }
                if (nextWords.isNotEmpty()) {
                    activeInspectedWord = null
                    val annotated = HomophoneAnnotator.annotate(nextWords, primaryIdx = 0, verbatimIdx = -1)
                    val curItems = (_candidateState.value as? CandidateStripState.Candidates)?.items
                    if (curItems != annotated) {
                        _candidateState.value = CandidateStripState.Candidates(annotated)
                        updateCandidatesVisibility()
                    }
                    return
                }
            }
            if (activeInspectedWord != null) {
                activeInspectedWord = null
                clearCandidates()
            }
            return
        }

        // Format English preview token in clean Title Case (e.g. "Amar", "Sonar", "Bangla") matching Gboard
        val formattedEnglish = rawEnglish.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        // Zero-allocation & zero-flicker deduplication:
        // If the user is navigating within or over the same word, just update cursor offsets
        // without re-querying the engine or triggering Compose recomposition.
        val prevInspected = activeInspectedWord
        if (prevInspected != null && prevInspected.word == word && prevInspected.rawPhonetic == rawEnglish) {
            activeInspectedWord = prevInspected.copy(
                charsBeforeCursor = beforeWordCount,
                charsAfterCursor = afterWordCount,
                hasTrailingSpace = false
            )
            return
        }

        activeInspectedWord = InspectedWord(
            word = word,
            charsBeforeCursor = beforeWordCount,
            charsAfterCursor = afterWordCount,
            rawPhonetic = rawEnglish,
            previewEnglish = formattedEnglish,
            hasTrailingSpace = false
        )

        // Query candidate suggestions from the Avro engine via wordInspectionSession with surrounding context
        val candidatesList = mutableListOf<String>()
        try {
            wordInspectionSession.reset()
            val sentenceBefore = before.substring(0, (before.length - beforeWordCount).coerceAtLeast(0)).trim()
            if (sentenceBefore.isNotEmpty()) {
                wordInspectionSession.setContext(sentenceBefore.takeLast(CONTEXT_CHAR_LIMIT))
            }
            val sentenceAfter = after.substring(afterWordCount.coerceAtMost(after.length)).trim()
            if (sentenceAfter.isNotEmpty()) {
                wordInspectionSession.setRightContext(sentenceAfter.take(64))
            }
            var lastRes: com.lekhani.android.ffi.TypingResult? = null
            for (ch in rawEnglish) {
                lastRes = wordInspectionSession.processKey(ch.toString())
            }
            if (lastRes != null) {
                candidatesList.addAll(lastRes.candidates)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying wordInspectionSession: ${e.message}")
        }

        // Build candidate list:
        // Slot 0: formattedEnglish preview (e.g. "Sonar", "Amar", "Bangla") with isVerbatimPreview = true
        // Slot 1: primary candidate (the word currently in document, e.g. "সোনার", "আমার", "বাংলা")
        // Slot 2+: alternative suggestions (e.g. "অমর", "বাংলার")
        val displayCandidates = mutableListOf<String>()
        val primaryCandidate = if (isBengali) word else (candidatesList.firstOrNull() ?: word)
        val isBengaliFirst = keyboardPrefs.avroStripOrder == KeyboardPreferences.STRIP_ORDER_BENGALI_FIRST

        if (keyboardPrefs.avroShowEnglishPreview) {
            if (isBengaliFirst) {
                // Slot 0: Primary Bengali candidate
                displayCandidates.add(primaryCandidate)
                // Slot 1: English preview token
                displayCandidates.add(formattedEnglish)
            } else {
                // Slot 0: English preview token (Classic)
                displayCandidates.add(formattedEnglish)
                // Slot 1: Primary Bengali candidate
                displayCandidates.add(primaryCandidate)
            }
        } else {
            // English preview disabled: Slot 0 is Bengali
            displayCandidates.add(primaryCandidate)
        }

        for (cand in candidatesList) {
            val isEnglishDup = cand.equals(formattedEnglish, ignoreCase = true) || cand.equals(rawEnglish, ignoreCase = true)
            if (!isEnglishDup && !displayCandidates.contains(cand) && !blacklist.isBlacklisted(cand)) {
                displayCandidates.add(cand)
            }
        }

        val primaryIdx = if (!keyboardPrefs.avroShowEnglishPreview || isBengaliFirst) 0 else 1
        val verbatimIdx = if (!keyboardPrefs.avroShowEnglishPreview) -1 else if (isBengaliFirst) 1 else 0

        val annotated = HomophoneAnnotator.annotate(
            displayCandidates,
            primaryIdx = primaryIdx,
            verbatimIdx = verbatimIdx
        )

        val curItems = (_candidateState.value as? CandidateStripState.Candidates)?.items
        if (curItems != annotated) {
            _candidateState.value = CandidateStripState.Candidates(annotated)
            updateCandidatesVisibility()
        }
    }

    // Constants

    companion object {
        private const val TAG = "LekhaniIME"
        private const val PREFS_NAME = KeyboardPreferences.PREFS_NAME
        const val PREF_LAYOUT = "active_layout"
        /**
         * Number of characters before the cursor fetched for AI context.
         * 256 chars covers ~2-3 sentences — sufficient for bigram/trigram scoring
         * without excessive Binder IPC payload.
         */
        private const val CONTEXT_CHAR_LIMIT = 256
        private val SMART_PUNCTUATION_CHARS = setOf('।', '॥', '.', ',', '?', '!', ';', ':', ')', ']', '}', '"', '\'', '\n')
        private val BENGALI_VOWEL_KARS = mapOf(
            'া' to "a",
            'ি' to "i",
            'ী' to "I",
            'ু' to "u",
            'ূ' to "U",
            'ৃ' to "rri",
            'ে' to "e",
            'ৈ' to "OI",
            'ো' to "o",
            'ৌ' to "OU"
        )
    }
}
