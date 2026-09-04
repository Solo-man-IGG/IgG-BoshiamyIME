package tw.igg.boshiamyime.service

import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import tw.igg.boshiamyime.R
import tw.igg.boshiamyime.data.DictionaryManager
import tw.igg.boshiamyime.engine.InputEngineManager
import tw.igg.boshiamyime.engine.LookupEngine
import tw.igg.boshiamyime.engine.T9Engine
import tw.igg.boshiamyime.engine.ZhuyinEngine
import tw.igg.boshiamyime.model.Candidate
import tw.igg.boshiamyime.model.KeyboardMode
import tw.igg.boshiamyime.ui.CandidateBarView
import tw.igg.boshiamyime.ui.KeyboardView

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

    private lateinit var btnMode: TextView
    private lateinit var btnSymbol: TextView
    private lateinit var btnEmoji: TextView

    private lateinit var prefs: SharedPreferences

    private var isShifted = false
    private var isCapsLock = false
    private var lastShiftTime = 0L
    private var zhuyinInput = ""
    private var lastCommittedChar = ""
    private var previousKeyboardMode: KeyboardMode = KeyboardMode.T9

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("boshiamy_prefs", MODE_PRIVATE)
        dictionaryManager = DictionaryManager(this)
        dictionaryManager.loadDictionary("standard")
        dictionaryManager.loadUsageFrequency(prefs)
        dictionaryManager.loadLearnedAssociations(prefs)

        lookupEngine = LookupEngine(dictionaryManager)
        t9Engine = T9Engine(dictionaryManager)
        zhuyinEngine = ZhuyinEngine(this)
        zhuyinEngine.loadDictionary()
        engineManager = InputEngineManager(lookupEngine, t9Engine)
        prefs.registerOnSharedPreferenceChangeListener(themeChangeListener)
    }

    private val themeChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && key.startsWith("theme_") && ::keyboardView.isInitialized) {
                loadThemeColors()
            }
        }

    override fun onCreateInputView(): View {
        val inflater = android.view.LayoutInflater.from(this)
        container = inflater.inflate(R.layout.keyboard_container, null)

        keyboardView = container.findViewById(R.id.boshiamy_keyboard_view)
        candidateBar = container.findViewById(R.id.candidate_bar)
        btnCandidateDelete = container.findViewById(R.id.btn_candidate_delete)
        btnMode = container.findViewById(R.id.btn_mode)
        btnSymbol = container.findViewById(R.id.btn_symbol)
        btnEmoji = container.findViewById(R.id.btn_emoji)

        keyboardView.setOnKeyPressListener(this)
        candidateBar.setOnCandidateClickListener(this)
        keyboardView.setLayout(KeyboardView.KeyboardLayout.T9)
        loadThemeColors()

        btnMode.setOnClickListener { toggleKeyboardMode() }
        btnCandidateDelete.setOnTouchListener(deleteTouchListener)
        btnSymbol.setOnClickListener {
            previousKeyboardMode = engineManager.keyboardMode
            engineManager.switchKeyboard(KeyboardMode.SYMBOL)
            keyboardView.setLayout(KeyboardView.KeyboardLayout.SYMBOL)
        }
        btnEmoji.setOnClickListener {
            previousKeyboardMode = engineManager.keyboardMode
            engineManager.switchKeyboard(KeyboardMode.EMOJI)
            keyboardView.setLayout(KeyboardView.KeyboardLayout.EMOJI)
        }

        updateTopBarButtons()

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val bottom = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars()
            ).bottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }

        return container
    }

    private fun loadThemeColors() {
        val bg = prefs.getInt("theme_bg", 0xFFD6D6D6.toInt())
        val text = prefs.getInt("theme_text", 0xFF000000.toInt())
        val keyBg = prefs.getInt("theme_key_bg", 0xFFFFFFFF.toInt())
        val keyPressed = prefs.getInt("theme_key_pressed", 0xFFCCCCCC.toInt())
        val border = prefs.getInt("theme_border", 0xFFB0B0B0.toInt())
        keyboardView.setThemeColors(bg, text, keyBg, keyPressed, border)
    }

    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        engineManager.clearInput()
        zhuyinInput = ""
        lastCommittedChar = ""
        candidateBar.setCandidates(emptyList())
        isShifted = false
        isCapsLock = false
        lastShiftTime = 0L
        keyboardView.setShifted(false)
    }

    override fun onKeyPress(key: String) {
        val inputConnection = currentInputConnection ?: return

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
                val isUri = (inputType and EditorInfo.TYPE_MASK_VARIATION) == EditorInfo.TYPE_TEXT_VARIATION_URI
                val isWebEdit = (inputType and EditorInfo.TYPE_MASK_VARIATION) == EditorInfo.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT
                val explicitAction = imeOptions and EditorInfo.IME_MASK_ACTION

                val hasSubmitAction = explicitAction in intArrayOf(
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_DONE,
                    EditorInfo.IME_ACTION_NEXT
                )

                when {
                    isUri || isWebEdit || hasSubmitAction -> {
                        val action = if (hasSubmitAction) explicitAction else EditorInfo.IME_ACTION_SEARCH
                        inputConnection.performEditorAction(action)
                        inputConnection.commitText("", 0)
                    }
                    isMultiline -> {
                        inputConnection.commitText("\n", 1)
                    }
                    else -> {
                        inputConnection.commitText("\n", 1)
                    }
                }
            }
            "space" -> {
                if (engineManager.keyboardMode == KeyboardMode.ZHUYIN) {
                    if (zhuyinInput.isNotEmpty()) {
                        commitFirstCandidateZhuyin()
                    } else {
                        inputConnection.commitText(" ", 1)
                    }
                } else if (engineManager.keyboardMode == KeyboardMode.T9) {
                    if (!engineManager.isEmpty) {
                        commitRawInput()
                    }
                    inputConnection.commitText(" ", 1)
                } else if (!engineManager.isEmpty) {
                    commitFirstCandidate()
                } else {
                    inputConnection.commitText(" ", 1)
                }
            }
            "⇧" -> {
                val now = System.currentTimeMillis()
                if (now - lastShiftTime <= 500) {
                    isCapsLock = !isCapsLock
                    isShifted = isCapsLock
                } else {
                    isCapsLock = false
                    isShifted = !isShifted
                }
                lastShiftTime = now
                keyboardView.setShifted(isShifted)
            }
            "🌐" -> {
                toggleKeyboardMode()
            }
            "SYM" -> {
                previousKeyboardMode = engineManager.keyboardMode
                engineManager.switchKeyboard(KeyboardMode.SYMBOL)
                keyboardView.setLayout(KeyboardView.KeyboardLayout.SYMBOL)
            }
            "✕" -> {
                returnToPreviousKeyboard()
            }
            "ABC" -> {
                engineManager.switchKeyboard(KeyboardMode.QWERTY)
                keyboardView.setLayout(KeyboardView.KeyboardLayout.QWERTY)
            }
            else -> {
                if (engineManager.keyboardMode == KeyboardMode.SYMBOL ||
                    engineManager.keyboardMode == KeyboardMode.EMOJI) {
                    inputConnection.commitText(key, 1)
                    returnToPreviousKeyboard()
                    return
                }
                if (engineManager.keyboardMode == KeyboardMode.ZHUYIN) {
                    zhuyinInput += zhuyinEngine.symbolToCode(key)
                    updateZhuyinCandidates()
                } else {
                    val typed = if (isShifted || isCapsLock) key.uppercase() else key
                    engineManager.appendInput(typed)
                    if (isShifted && !isCapsLock) {
                        isShifted = false
                        keyboardView.setShifted(false)
                    }
                    updateCandidates()
                }
            }
        }
    }

    override fun onLongPress(key: String) {
        val inputConnection = currentInputConnection ?: return

        when {
            engineManager.keyboardMode == KeyboardMode.T9 && key.length == 1 && key[0].isDigit() -> {
                inputConnection.commitText(key, 1)
            }
            engineManager.keyboardMode == KeyboardMode.QWERTY && key.length == 1 && key[0].isLetter() -> {
                val output = if (isShifted || isCapsLock) key.uppercase() else key
                inputConnection.commitText(output, 1)
                if (isShifted && !isCapsLock) {
                    isShifted = false
                    keyboardView.setShifted(false)
                }
            }
        }
    }

    override fun onCandidateClick(candidate: Candidate, position: Int) {
        commitCandidate(candidate)
    }

    private val deleteRepeatHandler = Handler(Looper.getMainLooper())
    private val deleteRepeatRunnable = object : Runnable {
        override fun run() {
            handleDelete()
            deleteRepeatHandler.postDelayed(this, 80)
        }
    }

    private val deleteTouchListener = View.OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                handleDelete()
                deleteRepeatHandler.postDelayed(deleteRepeatRunnable, 500)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                deleteRepeatHandler.removeCallbacks(deleteRepeatRunnable)
                true
            }
            else -> true
        }
    }

    private fun handleDelete() {
        if (engineManager.keyboardMode == KeyboardMode.ZHUYIN && zhuyinInput.isNotEmpty()) {
            zhuyinInput = zhuyinInput.dropLast(1)
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

        val currentInput = engineManager.currentInput
        showComposition(currentInput)
        if (currentInput.isNotEmpty()) {
            candidateBar.visibility = View.VISIBLE
        } else {
            candidateBar.visibility = View.GONE
        }
    }

    private fun updateZhuyinCandidates() {
        val candidates = zhuyinEngine.lookupPrefix(zhuyinInput)
        candidateBar.setCandidates(candidates)

        showComposition(zhuyinEngine.codeToBopomofo(zhuyinInput))
        if (zhuyinInput.isNotEmpty()) {
            candidateBar.visibility = View.VISIBLE
        } else {
            candidateBar.visibility = View.GONE
        }
    }

    private fun showComposition(text: String) {
        val inputConnection = currentInputConnection ?: return
        if (text.isEmpty()) {
            inputConnection.finishComposingText()
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
        candidateBar.showError("查無此字")
        candidateBar.visibility = View.VISIBLE
    }

    private fun commitFirstCandidateZhuyin() {
        val candidates = zhuyinEngine.lookupPrefix(zhuyinInput)
        if (candidates.isNotEmpty()) {
            commitCandidate(candidates.first())
        } else {
            commitRawZhuyin()
        }
    }

    private fun commitRawInput() {
        val inputConnection = currentInputConnection ?: return
        val input = engineManager.currentInput
        if (input.isNotEmpty()) {
            inputConnection.commitText(input, 1)
            lastCommittedChar = ""
            engineManager.clearInput()
            candidateBar.setCandidates(emptyList())
            candidateBar.visibility = View.GONE
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
            candidateBar.setCandidates(emptyList())
            candidateBar.visibility = View.GONE
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

        val associations = lookupEngine.lookupAssociations(candidate.char)
        if (associations.isNotEmpty()) {
            candidateBar.setCandidates(associations)
            candidateBar.visibility = View.VISIBLE
        } else {
            val bopomofo = zhuyinEngine.lookupBopomofoByChar(candidate.char)
            if (bopomofo.isNotEmpty()) {
                candidateBar.showBopomofo(bopomofo)
                candidateBar.visibility = View.VISIBLE
            } else {
                candidateBar.setCandidates(emptyList())
            }
        }
    }

    private fun trackUsage(char: String) {
        val current = prefs.getInt("freq_$char", 0)
        prefs.edit().putInt("freq_$char", current + 1).apply()
        dictionaryManager.recordUsage(char)
    }

    private fun saveLearnedAssociations() {
        dictionaryManager.saveLearnedAssociations(prefs)
    }

    override fun onFinishInput() {
        super.onFinishInput()
        currentInputConnection?.finishComposingText()
        engineManager.clearInput()
        zhuyinInput = ""
        lastCommittedChar = ""
        candidateBar.setCandidates(emptyList())
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

        val layout = when (nextMode) {
            KeyboardMode.T9 -> KeyboardView.KeyboardLayout.T9
            KeyboardMode.QWERTY -> KeyboardView.KeyboardLayout.QWERTY
            KeyboardMode.ZHUYIN -> KeyboardView.KeyboardLayout.ZHUYIN
            KeyboardMode.SYMBOL -> KeyboardView.KeyboardLayout.SYMBOL
            KeyboardMode.EMOJI -> KeyboardView.KeyboardLayout.EMOJI
        }
        keyboardView.setLayout(layout)
        isShifted = false
        keyboardView.setShifted(false)
        updateTopBarButtons()
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

    private fun updateTopBarButtons() {
        btnMode.text = engineManager.keyboardMode.displayName
    }
}
