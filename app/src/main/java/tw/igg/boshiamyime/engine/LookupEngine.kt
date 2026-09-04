package tw.igg.boshiamyime.engine

import tw.igg.boshiamyime.data.DictionaryManager
import tw.igg.boshiamyime.model.Candidate

class LookupEngine(private val dictionary: DictionaryManager) {

    companion object {
        const val MAX_CANDIDATES = 9
        const val MAX_ASSOCIATIONS = 50
    }

    fun lookupExact(code: String): List<Candidate> {
        return dictionary.lookup(code)
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = true
                )
            }
            .sortedWith(candidateOrder)
    }

    fun lookupPrefix(code: String): List<Candidate> {
        return dictionary.lookupPrefix(code)
            .map { entry ->
                Candidate(
                    code = entry.code,
                    char = entry.char,
                    frequency = entry.frequency,
                    isExactMatch = entry.code == code
                )
            }
            .sortedWith(candidateOrder)
    }

    fun lookupAssociations(char: String): List<Candidate> {
        val associatedChars = dictionary.getAssociations(char)
        return associatedChars.map { assocChar ->
            val entry = dictionary.lookupByChar(assocChar)
            Candidate(
                code = entry?.code ?: "",
                char = assocChar,
                frequency = entry?.frequency ?: 0
            )
        }.take(MAX_ASSOCIATIONS)
    }

    private val candidateOrder: Comparator<Candidate> = Comparator { a, b ->
        // 1. 完全命中優先
        if (a.isExactMatch != b.isExactMatch) return@Comparator if (a.isExactMatch) -1 else 1
        // 2. 單碼、雙碼快選在前（碼越短越前）
        if (a.code.length != b.code.length) return@Comparator a.code.length.compareTo(b.code.length)
        // 3. 依有效使用次數（含使用者使用頻率）
        val aFreq = dictionary.effectiveFrequency(a.code, a.char)
        val bFreq = dictionary.effectiveFrequency(b.code, b.char)
        bFreq.compareTo(aFreq)
    }
}
