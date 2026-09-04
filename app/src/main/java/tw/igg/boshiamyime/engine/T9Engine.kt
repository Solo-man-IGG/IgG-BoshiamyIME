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

        val allEntries = dictionary.getAllEntries()

        val exactMatches = allEntries
            .filter { entry ->
                entry.t9.isNotEmpty() && entry.t9 == t9Sequence
            }
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = true
                )
            }

        if (exactMatches.isNotEmpty()) {
            return exactMatches
                .sortedWith(candidateOrder)
                .take(MAX_CANDIDATES)
        }

        return allEntries
            .filter { entry ->
                if (entry.code.isEmpty()) return@filter false
                val entryT9 = codeToT9(entry.code)
                entryT9.startsWith(t9Sequence)
            }
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = false
                )
            }
            .sortedWith(candidateOrder)
            .take(MAX_CANDIDATES)
    }

    private val candidateOrder: Comparator<Candidate> = Comparator { a, b ->
        if (a.isExactMatch != b.isExactMatch) return@Comparator if (a.isExactMatch) -1 else 1
        if (a.code.length != b.code.length) return@Comparator a.code.length.compareTo(b.code.length)
        val aFreq = dictionary.effectiveFrequency(a.code, a.char)
        val bFreq = dictionary.effectiveFrequency(b.code, b.char)
        bFreq.compareTo(aFreq)
    }

    fun isT9Key(key: String): Boolean {
        return key.length == 1 && key[0] in '2'..'9'
    }
}
