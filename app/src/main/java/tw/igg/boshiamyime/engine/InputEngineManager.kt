package tw.igg.boshiamyime.engine

import tw.igg.boshiamyime.model.BoshiamyMode
import tw.igg.boshiamyime.model.Candidate
import tw.igg.boshiamyime.model.KeyboardMode

class InputEngineManager(
    private val lookupEngine: LookupEngine,
    private val t9Engine: T9Engine
) {
    var keyboardMode: KeyboardMode = KeyboardMode.T9
        private set

    var boshiamyMode: BoshiamyMode = BoshiamyMode.STANDARD
        private set

    private val inputBuffer = StringBuilder()

    val currentInput: String
        get() = inputBuffer.toString()

    val isEmpty: Boolean
        get() = inputBuffer.isEmpty()

    fun appendInput(key: String) {
        inputBuffer.append(key)
    }

    fun deleteLastInput() {
        if (inputBuffer.isNotEmpty()) {
            inputBuffer.deleteCharAt(inputBuffer.length - 1)
        }
    }

    fun clearInput() {
        inputBuffer.clear()
    }

    fun getCandidates(): List<Candidate> {
        val input = inputBuffer.toString()
        if (input.isEmpty()) return emptyList()
        val query = input.lowercase()

        return when (keyboardMode) {
            KeyboardMode.T9 -> t9Engine.predict(query)
            KeyboardMode.QWERTY -> lookupEngine.lookupPrefix(query)
            KeyboardMode.ZHUYIN -> emptyList()
            KeyboardMode.SYMBOL -> emptyList()
            KeyboardMode.EMOJI -> emptyList()
        }
    }

    fun getExactCandidates(): List<Candidate> {
        val input = inputBuffer.toString()
        if (input.isEmpty()) return emptyList()
        val query = input.lowercase()

        return when (keyboardMode) {
            KeyboardMode.T9 -> t9Engine.predict(query)
            KeyboardMode.QWERTY -> lookupEngine.lookupExact(query)
            KeyboardMode.ZHUYIN -> emptyList()
            KeyboardMode.SYMBOL -> emptyList()
            KeyboardMode.EMOJI -> emptyList()
        }
    }

    fun switchKeyboard(mode: KeyboardMode) {
        keyboardMode = mode
        clearInput()
    }

    fun switchBoshiamy(mode: BoshiamyMode) {
        boshiamyMode = mode
        clearInput()
    }

    fun isT9Input(key: String): Boolean {
        return keyboardMode == KeyboardMode.T9 && t9Engine.isT9Key(key)
    }
}
