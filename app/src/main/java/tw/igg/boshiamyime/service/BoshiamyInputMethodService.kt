package tw.igg.boshiamyime.service

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.res.Configuration
import android.inputmethodservice.InputMethodService
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tw.igg.boshiamyime.R
import tw.igg.boshiamyime.data.DictionaryManager
import tw.igg.boshiamyime.engine.InputEngineManager
import tw.igg.boshiamyime.engine.LookupEngine
import tw.igg.boshiamyime.engine.T9Engine
import tw.igg.boshiamyime.engine.ZhuyinEngine
import tw.igg.boshiamyime.model.Candidate
import tw.igg.boshiamyime.model.KeyboardMode
import tw.igg.boshiamyime.theme.ThemePalette
import tw.igg.boshiamyime.ui.CandidateBarView
import tw.igg.boshiamyime.ui.KeyboardView

@Suppress("SpellCheckingInspection")
class BoshiamyInputMethodService : InputMethodService(),
    KeyboardView.OnKeyPressListener,
    CandidateBarView.OnCandidateClickListener {

    private lateinit var dictionaryManager: DictionaryManager
    private lateinit var lookupEngine: LookupEngine
    private lateinit var t9Engine: T9Engine
    private lateinit var zhuyinEngine: ZhuyinEngine
    private lateinit var engineManager: InputEngineManager

    private lateinit var keyboardView: KeyboardView
    private lateinit var candidateBar: CandidateBarView
    private lateinit var container: View
    private lateinit var btnCandidateDelete: TextView

    private lateinit var prefs: SharedPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isDictionaryReady = false

    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTime = 0L
    private var zhuyinInput = ""
    private var zhuyinComplete = false
    private var lastCommittedChar = ""
    private var previousKeyboardMode: KeyboardMode = KeyboardMode.T9
    private var lastSavedAssociationsTime = 0L
    private val associationsSaveInterval = 5000L
    private var soundPool: SoundPool? = null
    private var keyClickSoundId = 0
    private var soundReady = false

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("boshiamy_prefs", MODE_PRIVATE)
        dictionaryManager = DictionaryManager(this)

        lookupEngine = LookupEngine(dictionaryManager)
        t9Engine = T9Engine(dictionaryManager)
        zhuyinEngine = ZhuyinEngine(this)
        engineManager = InputEngineManager(lookupEngine, t9Engine)
        prefs.registerOnSharedPreferenceChangeListener(themeChangeListener)

        val initialMode = when (prefs.getString("default_input_mode", "T9")) {
            "QWERTY" -> KeyboardMode.QWERTY
            "ZHUYIN" -> KeyboardMode.ZHUYIN
            else -> KeyboardMode.T9
        }
        engineManager.switchKeyboard(initialMode)

        serviceScope.launch {
            withContext(Dispatchers.IO) {
                Log.i("BoshiamyIME", "Dictionary loading started")
                dictionaryManager.loadDictionary("standard")
                dictionaryManager.loadUsageFrequency(prefs)
                dictionaryManager.loadLearnedAssociations(prefs)
                zhuyinEngine.loadDictionary()
                Log.i("BoshiamyIME", "Dictionary loading finished")
            }
            isDictionaryReady = true
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::keyboardView.isInitialized &&
            prefs.getString("theme_mode", ThemePalette.MODE_SYSTEM) == ThemePalette.MODE_SYSTEM) {
            loadThemeColors()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(themeChangeListener)
        soundPool?.release()
        soundPool = null
        super.onDestroy()
    }

    private val themeChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!::keyboardView.isInitialized) return@OnSharedPreferenceChangeListener
            when {
                key != null && key.startsWith("theme_") -> loadThemeColors()
                key == "keyboard_scale" -> keyboardView.setScale(prefs.getFloat("keyboard_scale", 1.0f))
                key == "delete_key_location" -> applyDeleteKeyLocation()
            }
        }

    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        val inflater = LayoutInflater.from(this)
        container = inflater.inflate(R.layout.keyboard_container, null)

        keyboardView = container.findViewById(R.id.boshiamy_keyboard_view)
        candidateBar = container.findViewById(R.id.candidate_bar)
        btnCandidateDelete = container.findViewById(R.id.btn_candidate_delete)

        setupSoundPool()

        keyboardView.setOnKeyPressListener(this)
        candidateBar.setOnCandidateClickListener(this)
        val initialLayout = when (engineManager.keyboardMode) {
            KeyboardMode.QWERTY -> KeyboardView.KeyboardLayout.QWERTY
            KeyboardMode.ZHUYIN -> KeyboardView.KeyboardLayout.ZHUYIN
            else -> KeyboardView.KeyboardLayout.T9
        }
        keyboardView.setLayout(initialLayout)
        keyboardView.setModeLabel(engineManager.keyboardMode.displayName)
        keyboardView.setScale(prefs.getFloat("keyboard_scale", 1.0f))
        applyDeleteKeyLocation()
        loadThemeColors()

        btnCandidateDelete.setOnTouchListener(deleteTouchListener)

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            ).bottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }

        return container
    }

    private fun setupSoundPool() {
        soundPool?.release()
        soundPool = null
        soundPool = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        keyClickSoundId = 0
        soundReady = false
        soundPool?.setOnLoadCompleteListener { _, _, status ->
            soundReady = status == 0
        }
        keyClickSoundId = soundPool?.load(this, R.raw.key_click, 1) ?: 0
    }

    private fun loadThemeColors() {
        val palette = ThemePalette.resolve(prefs, this)
        keyboardView.setThemeColors(
            palette.bg, palette.text, palette.keyBg, palette.keyPressed, palette.border
        )
        container.setBackgroundColor(palette.bg)
        candidateBar.setThemeColors(palette.text, palette.bg)
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        engineManager.clearInput()
        zhuyinInput = ""
        zhuyinComplete = false
        lastCommittedChar = ""
        candidateBar.setCandidates(emptyList())
        keyboardView.setSpaceHint("")
        candidateBar.visibility = View.VISIBLE
        isShifted = false
        isCapsLock = false
        lastShiftTime = 0L
        keyboardView.setShiftState(false, false)
    }

    private fun applyDeleteKeyLocation() {
        if (!::keyboardView.isInitialized) return
        val bottom = prefs.getString("delete_key_location", "enter") != "top"
        keyboardView.setBottomDeleteEnabled(bottom)
        btnCandidateDelete.visibility = if (bottom) View.GONE else View.VISIBLE
    }

    override fun onKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return
        performHaptic()

        when (key) {
            "⌫" -> {
                handleDelete()
            }
            "⏎" -> {
                val composing = !engineManager.isEmpty ||
                    (engineManager.keyboardMode == KeyboardMode.ZHUYIN && zhuyinInput.isNotEmpty())
                if (composing) {
                    commitRawInput()
                    return
                }
                val editorInfo = currentInputEditorInfo
                val inputType = editorInfo?.inputType ?: 0
                val imeOptions = editorInfo?.imeOptions ?: 0

                val isMultiline = (inputType and EditorInfo.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
                val isUri = variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                val isWebEdit = variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                val explicitAction = imeOptions and EditorInfo.IME_MASK_ACTION
                val isSearchAction = explicitAction == EditorInfo.IME_ACTION_SEARCH ||
                    explicitAction == EditorInfo.IME_ACTION_GO

                val hasSubmitAction = explicitAction in intArrayOf(
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_NEXT
                )

                when {
                    isUri || isWebEdit -> {
                        inputConnection.performEditorAction(EditorInfo.IME_ACTION_SEARCH)
                        inputConnection.commitText("", 0)
                    }
                    isSearchAction -> {
                        inputConnection.performEditorAction(explicitAction)
                        inputConnection.commitText("", 0)
                    }
                    isMultiline -> {
                        inputConnection.commitText("\n", 1)
                    }
                    hasSubmitAction -> {
                        inputConnection.performEditorAction(explicitAction)
                        inputConnection.commitText("", 0)
                    }
                    else -> {
                        inputConnection.commitText("\n", 1)
                    }
                }
            }
            "space" -> when {
                engineManager.keyboardMode == KeyboardMode.ZHUYIN ->
                    if (zhuyinInput.isNotEmpty()) {
                        if (!zhuyinComplete) {
                            zhuyinComplete = true
                            updateZhuyinCandidates()
                        }
                    } else {
                        commitDirectText(" ")
                    }
                engineManager.keyboardMode == KeyboardMode.T9 -> {
                    if (!engineManager.isEmpty) {
                        commitRawInput()
                    }
                    commitDirectText(" ")
                }
                !engineManager.isEmpty -> commitFirstCandidate()
                else -> commitDirectText(" ")
            }
            "⇧" -> {
                val now = System.currentTimeMillis()
                val doubleTap = now - lastShiftTime <= 500
                when {
                    doubleTap -> {
                        isCapsLock = !isCapsLock
                        isShifted = isCapsLock
                    }
                    isCapsLock -> {
                        isCapsLock = false
                        isShifted = false
                    }
                    else -> {
                        isShifted = !isShifted
                    }
                }
                lastShiftTime = now
                keyboardView.setShiftState(isShifted, isCapsLock)
            }
            "🌐" -> {
                toggleKeyboardMode()
            }
            "SYM" -> {
                openPanel(KeyboardMode.SYMBOL, KeyboardView.KeyboardLayout.SYMBOL)
            }
            "✕" -> {
                returnToPreviousKeyboard()
            }
            "ABC" -> {
                engineManager.switchKeyboard(KeyboardMode.QWERTY)
                keyboardView.setLayout(KeyboardView.KeyboardLayout.QWERTY)
            }
            "mode" -> {
                toggleKeyboardMode()
            }
            "sym" -> {
                openPanel(KeyboardMode.SYMBOL, KeyboardView.KeyboardLayout.SYMBOL)
            }
            "😊" -> {
                if (engineManager.keyboardMode == KeyboardMode.EMOJI) {
                    inputConnection.commitText(key, 1)
                    returnToPreviousKeyboard()
                } else {
                    openPanel(KeyboardMode.EMOJI, KeyboardView.KeyboardLayout.EMOJI)
                }
            }
            else -> {
                if (engineManager.keyboardMode == KeyboardMode.SYMBOL ||
                    engineManager.keyboardMode == KeyboardMode.EMOJI) {
                    commitDirectText(key)
                    returnToPreviousKeyboard()
                    return
                }
                if (engineManager.keyboardMode == KeyboardMode.ZHUYIN) {
                    val code = zhuyinEngine.symbolToCode(key)
                    val isTone = zhuyinEngine.isToneKey(code)
                    when {
                        isTone && zhuyinComplete -> {
                        }
                        isTone && zhuyinInput.isEmpty() -> {
                        }
                        zhuyinComplete -> {
                            zhuyinInput = ""
                            zhuyinInput += code
                            zhuyinComplete = false
                            updateZhuyinCandidates()
                        }
                        else -> {
                            zhuyinInput += code
                            if (isTone) zhuyinComplete = true
                            updateZhuyinCandidates()
                        }
                    }
                } else {
                    if (engineManager.keyboardMode == KeyboardMode.QWERTY) {
                        if ((isShifted || isCapsLock) && key.length == 1 &&
                            (key[0].isLetter() || key == "," || key == ".")) {
                            flushAndCommitDirect(key.uppercase())
                            if (isShifted && !isCapsLock) {
                                isShifted = false
                                lastShiftTime = 0L
                                keyboardView.setShiftState(false, false)
                            }
                            return
                        }
                        if (key.length == 1 && key[0].isDigit()) {
                            flushAndCommitDirect(key)
                            return
                        }
                    }
                    val typed = if (isShifted || isCapsLock) key.uppercase() else key
                    engineManager.appendInput(typed)
                    if (isShifted && !isCapsLock) {
                        isShifted = false
                        lastShiftTime = 0L
                        keyboardView.setShiftState(false, false)
                    }
                    updateCandidates()
                }
            }
        }
    }

    override fun onLongPress(key: String) {
        if (currentInputConnection == null) return
        performHaptic()

        when {
            engineManager.keyboardMode == KeyboardMode.T9 && key.length == 1 && key[0].isDigit() -> {
                commitDirectText(key)
            }
            engineManager.keyboardMode == KeyboardMode.QWERTY && key.length == 1 &&
                (key[0].isLetter() || key == "," || key == ".") -> {
                val output = if (isShifted || isCapsLock) key.uppercase() else key
                flushAndCommitDirect(output)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                    lastShiftTime = 0L
                    keyboardView.setShiftState(false, false)
                }
            }
        }
    }

    override fun onCandidateClick(candidate: Candidate, position: Int) {
        performHaptic()
        commitCandidate(candidate)
    }

    private val deleteRepeatHandler = Handler(Looper.getMainLooper())
    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            handleDelete()
            deleteRepeatHandler.postDelayed(this, 80)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val deleteTouchListener = View.OnTouchListener { view, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleDelete()
                deleteRepeatHandler.postDelayed(deleteRepeatRunnable, 500)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                deleteRepeatHandler.removeCallbacks(deleteRepeatRunnable)
                view.performClick()
                true
            }
            else -> true
        }
    }

    private fun handleDelete() {
        if (engineManager.keyboardMode == KeyboardMode.ZHUYIN && zhuyinInput.isNotEmpty()) {
            zhuyinInput = zhuyinInput.dropLast(1)
            zhuyinComplete = false
            updateZhuyinCandidates()
        } else if (!engineManager.isEmpty) {
            engineManager.deleteLastInput()
            updateCandidates()
        } else {
            deleteEditorGrapheme()
        }
    }

    private fun deleteEditorGrapheme() {
        val inputConnection = currentInputConnection ?: return
        val selected = inputConnection.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            inputConnection.commitText("", 1)
            return
        }
        val before = inputConnection.getTextBeforeCursor(2, 0)?.toString() ?: ""
        if (before.isEmpty()) {
            inputConnection.deleteSurroundingText(1, 0)
            return
        }
        var count = 1
        val last = before.last()
        if (Character.isHighSurrogate(last) && before.length >= 2) {
            count = 2
        } else if (before.length >= 2) {
            val prev = before[before.length - 2]
            if (Character.isHighSurrogate(prev) && Character.isLowSurrogate(last)) {
                count = 2
            }
        }
        inputConnection.deleteSurroundingText(count, 0)
    }

    private fun updateCandidates() {
        val allCandidates = engineManager.getCandidates()
        candidateBar.setCandidates(allCandidates)
        keyboardView.setSpaceHint("")

        val currentInput = engineManager.currentInput
        showComposition(currentInput)
        candidateBar.visibility = View.VISIBLE
    }

    private fun updateZhuyinCandidates() {
        val candidates = if (zhuyinComplete) {
            zhuyinEngine.lookup(zhuyinInput)
        } else {
            zhuyinEngine.lookupPrefix(zhuyinInput)
        }
        candidateBar.setCandidates(candidates)
        keyboardView.setSpaceHint("")

        showComposition(zhuyinEngine.codeToBopomofo(zhuyinInput))
        candidateBar.visibility = View.VISIBLE
    }

    private fun showComposition(text: String) {
        val inputConnection = currentInputConnection ?: return
        if (text.isEmpty()) {
            inputConnection.commitText("", 1)
        } else {
            inputConnection.setComposingText(text, 1)
        }
    }

    private fun commitFirstCandidate() {
        if (engineManager.keyboardMode == KeyboardMode.QWERTY) {
            val exact = engineManager.getExactCandidates()
            if (exact.isNotEmpty()) {
                commitCandidate(exact.first())
            } else {
                showInputError()
            }
        } else {
            val candidates = engineManager.getCandidates()
            if (candidates.isNotEmpty()) {
                commitCandidate(candidates.first())
            } else {
                commitRawInput()
            }
        }
    }

    private fun showInputError() {
        engineManager.clearInput()
        zhuyinInput = ""
        zhuyinComplete = false
        currentInputConnection?.finishComposingText()
        candidateBar.setCandidates(emptyList())
        candidateBar.showError("查無此字")
        candidateBar.visibility = View.VISIBLE
    }

    private fun commitRawInput() {
        val inputConnection = currentInputConnection ?: return
        val input = engineManager.currentInput
        if (input.isNotEmpty()) {
            inputConnection.commitText(input, 1)
            lastCommittedChar = ""
            engineManager.clearInput()
            candidateBar.setCandidates(emptyList())
        } else if (engineManager.keyboardMode == KeyboardMode.ZHUYIN && zhuyinInput.isNotEmpty()) {
            commitRawZhuyin()
        }
    }

    private fun commitRawZhuyin() {
        val inputConnection = currentInputConnection ?: return
        if (zhuyinInput.isNotEmpty()) {
            inputConnection.commitText(zhuyinInput, 1)
            lastCommittedChar = ""
            zhuyinInput = ""
            zhuyinComplete = false
            candidateBar.setCandidates(emptyList())
        }
    }

    private fun commitCandidate(candidate: Candidate) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(candidate.char, 1)
        trackUsage(candidate.char)
        if (lastCommittedChar.isNotEmpty()) {
            dictionaryManager.recordBigram(lastCommittedChar, candidate.char)
            saveLearnedAssociations()
        }
        lastCommittedChar = candidate.char
        engineManager.clearInput()
        zhuyinInput = ""
        zhuyinComplete = false

        val bopomofo = zhuyinEngine.lookupBopomofoByChar(candidate.char)
        keyboardView.setSpaceHint(bopomofo)

        val associations = lookupEngine.lookupAssociations(candidate.char)
        if (associations.isNotEmpty()) {
            candidateBar.setCandidates(associations)
            candidateBar.visibility = View.VISIBLE
        } else {
            candidateBar.setCandidates(emptyList())
        }
    }

    private fun trackUsage(char: String) {
        val current = prefs.getInt("freq_$char", 0)
        prefs.edit { putInt("freq_$char", current + 1) }
        dictionaryManager.recordUsage(char)
    }

    @SuppressLint("MissingPermission")
    private fun performHaptic() {
        if (prefs.getBoolean("vibrate", true)) {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                val strength = prefs.getInt("vibrate_strength", 50).coerceIn(0, 100)
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        if (strength > 0) {
                            val amplitude = (strength * 254 / 100) + 1
                            vibrator.vibrate(
                                VibrationEffect.createOneShot(18, amplitude)
                            )
                        }
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate((10 + strength / 5).toLong())
                    }
                }
            }
        }
        if (prefs.getBoolean("sound", false)) {
            if (keyClickSoundId != 0 && soundReady) {
                if (soundPool?.play(keyClickSoundId, 1f, 1f, 1, 0, 1f) == 0) {
                    setupSoundPool()
                }
            }
        }
    }

    private fun commitDirectText(text: String) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(toFullWidth(text), 1)
    }

    private fun flushAndCommitDirect(text: String) {
        commitRawInput()
        commitDirectText(text)
    }

    private fun toFullWidth(text: String): String {
        if (!prefs.getBoolean("full_width", false)) return text
        return buildString {
            for (c in text) {
                when (c) {
                    ' ' -> append('\u3000')
                    in '!'..'~' -> append((c.code + 0xFEE0).toChar())
                    else -> append(c)
                }
            }
        }
    }

    private fun saveLearnedAssociations() {
        val now = System.currentTimeMillis()
        if (now - lastSavedAssociationsTime >= associationsSaveInterval) {
            lastSavedAssociationsTime = now
            dictionaryManager.saveLearnedAssociations(prefs)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentInputConnection?.finishComposingText()
        engineManager.clearInput()
        zhuyinInput = ""
        zhuyinComplete = false
        if (lastCommittedChar.isNotEmpty()) {
            dictionaryManager.saveLearnedAssociations(prefs)
        }
        lastCommittedChar = ""
        if (::candidateBar.isInitialized) {
            candidateBar.setCandidates(emptyList())
        }
        if (::keyboardView.isInitialized) {
            keyboardView.setSpaceHint("")
        }
        isShifted = false
        isCapsLock = false
        lastShiftTime = 0L
    }

    private fun toggleKeyboardMode() {
        val nextMode = when (engineManager.keyboardMode) {
            KeyboardMode.T9 -> KeyboardMode.QWERTY
            KeyboardMode.QWERTY -> KeyboardMode.ZHUYIN
            KeyboardMode.ZHUYIN -> KeyboardMode.T9
            KeyboardMode.SYMBOL -> previousKeyboardMode
            KeyboardMode.EMOJI -> previousKeyboardMode
        }
        engineManager.switchKeyboard(nextMode)
        zhuyinInput = ""
        zhuyinComplete = false

        val layout = when (nextMode) {
            KeyboardMode.T9 -> KeyboardView.KeyboardLayout.T9
            KeyboardMode.QWERTY -> KeyboardView.KeyboardLayout.QWERTY
            KeyboardMode.ZHUYIN -> KeyboardView.KeyboardLayout.ZHUYIN
            KeyboardMode.SYMBOL -> KeyboardView.KeyboardLayout.SYMBOL
            KeyboardMode.EMOJI -> KeyboardView.KeyboardLayout.EMOJI
        }
        keyboardView.setLayout(layout)
        keyboardView.setModeLabel(nextMode.displayName)
        isShifted = false
        isCapsLock = false
        lastShiftTime = 0L
        keyboardView.setShiftState(false, false)
    }

    private fun openPanel(mode: KeyboardMode, layout: KeyboardView.KeyboardLayout) {
        if (engineManager.keyboardMode != KeyboardMode.SYMBOL &&
            engineManager.keyboardMode != KeyboardMode.EMOJI) {
            previousKeyboardMode = engineManager.keyboardMode
        }
        engineManager.switchKeyboard(mode)
        keyboardView.setLayout(layout)
    }

    private fun returnToPreviousKeyboard() {
        engineManager.switchKeyboard(previousKeyboardMode)
        val layout = when (previousKeyboardMode) {
            KeyboardMode.T9 -> KeyboardView.KeyboardLayout.T9
            KeyboardMode.QWERTY -> KeyboardView.KeyboardLayout.QWERTY
            KeyboardMode.ZHUYIN -> KeyboardView.KeyboardLayout.ZHUYIN
            KeyboardMode.SYMBOL -> KeyboardView.KeyboardLayout.SYMBOL
            KeyboardMode.EMOJI -> KeyboardView.KeyboardLayout.EMOJI
        }
        keyboardView.setLayout(layout)
    }
}
