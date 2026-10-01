package tw.igg.boshiamyime.engine

import tw.igg.boshiamyime.data.DictionaryManager
import tw.igg.boshiamyime.model.Candidate
import tw.igg.boshiamyime.util.GlyphSupport

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
        return associatedChars
            .filter { GlyphSupport.canDisplay(it) }
            .map { assocChar ->
                val entry = dictionary.lookupByChar(assocChar)
                Candidate(
                    code = entry?.code ?: "",
                    char = assocChar,
                    frequency = entry?.frequency ?: 0
                )
            }.take(MAX_ASSOCIATIONS)
    }

    private val candidateOrder: Comparator<Candidate> = Comparator { a, b ->
        // 0. 系統字型畫不出來（會顯示成方塊）的一律沉到最後
        val aGlyph = GlyphSupport.canDisplay(a.char)
        val bGlyph = GlyphSupport.canDisplay(b.char)
        if (aGlyph != bGlyph) return@Comparator if (aGlyph) -1 else 1
        // 1. 完全命中優先
        if (a.isExactMatch != b.isExactMatch) return@Comparator if (a.isExactMatch) -1 else 1
        // 2. 有效使用次數優先（使用者學過的字要能浮到前面）
        val aFreq = dictionary.effectiveFrequency(a.code, a.char)
        val bFreq = dictionary.effectiveFrequency(b.code, b.char)
        if (aFreq != bFreq) return@Comparator bFreq.compareTo(aFreq)
        // 3. 同頻率時短碼在前（打字省事）
        a.code.length.compareTo(b.code.length)
    }
}
