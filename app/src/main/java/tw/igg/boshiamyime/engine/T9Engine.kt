package tw.igg.boshiamyime.engine

import tw.igg.boshiamyime.data.DictionaryManager
import tw.igg.boshiamyime.model.Candidate

class T9Engine(private val dictionary: DictionaryManager) {

    companion object {
        const val MAX_CANDIDATES = 9

        private val t9Map = mapOf(
            '2' to "abc",
            '3' to "def",
            '4' to "ghi",
            '5' to "jkl",
            '6' to "mno",
            '7' to "pqrs",
            '8' to "tuv",
            '9' to "wxyz"
        )

        private val charToT9: Map<Char, Char> by lazy {
            buildMap {
                t9Map.forEach { (key, chars) ->
                    chars.forEach { put(it, key) }
                }
            }
        }
    }

    fun codeToT9(code: String): String {
        return code.map { char ->
            charToT9[char] ?: char
        }.joinToString("")
    }

    fun predict(t9Sequence: String): List<Candidate> {
        if (t9Sequence.isEmpty()) return emptyList()

        val exactMatches = dictionary.lookupT9(t9Sequence)
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = true,
                    originalOrder = entry.originalOrder
                )
            }

        if (exactMatches.isNotEmpty()) {
            return exactMatches
                .sortedWith(candidateOrder)
                .take(MAX_CANDIDATES)
        }

        return dictionary.lookupT9Prefix(t9Sequence)
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = false,
                    originalOrder = entry.originalOrder
                )
            }
            .sortedWith(candidateOrder)
            .take(MAX_CANDIDATES)
    }

    private val candidateOrder: Comparator<Candidate> = Comparator { a, b ->
        if (a.isExactMatch != b.isExactMatch) return@Comparator if (a.isExactMatch) -1 else 1
        // T9 只是輸入方式，候選順序仍跟嘸蝦米（boshiamy.cin）走。
        val aSimplified = a.frequency < 0
        val bSimplified = b.frequency < 0
        if (aSimplified != bSimplified) return@Comparator if (aSimplified) 1 else -1
        val aCount = dictionary.getUserFrequency(a.code, a.char)
        val bCount = dictionary.getUserFrequency(b.code, b.char)
        val aLearned = aCount >= DictionaryManager.LEARN_THRESHOLD
        val bLearned = bCount >= DictionaryManager.LEARN_THRESHOLD
        if (aLearned != bLearned) return@Comparator if (aLearned) -1 else 1
        if (aLearned && aCount != bCount) return@Comparator bCount.compareTo(aCount)
        if (a.originalOrder != b.originalOrder) {
            return@Comparator a.originalOrder.compareTo(b.originalOrder)
        }
        a.code.length.compareTo(b.code.length)
    }

    fun isT9Key(key: String): Boolean {
        return key.length == 1 && key[0] in '2'..'9'
    }
}
