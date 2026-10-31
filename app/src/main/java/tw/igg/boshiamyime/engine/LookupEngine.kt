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
                    isExactMatch = true,
                    originalOrder = entry.originalOrder
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
                    isExactMatch = entry.code == code,
                    originalOrder = entry.originalOrder
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
                    frequency = entry?.frequency ?: 0,
                    originalOrder = entry?.originalOrder ?: 0
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
        // 2. 簡體專用字沉到正體之後
        val aSimplified = a.frequency < 0
        val bSimplified = b.frequency < 0
        if (aSimplified != bSimplified) return@Comparator if (aSimplified) 1 else -1
        // 3. 同一個「碼 + 字」選滿 3 次才前移；未滿 3 次完全不影響原順序
        val aCount = dictionary.getUserFrequency(a.code, a.char)
        val bCount = dictionary.getUserFrequency(b.code, b.char)
        val aLearned = aCount >= DictionaryManager.LEARN_THRESHOLD
        val bLearned = bCount >= DictionaryManager.LEARN_THRESHOLD
        if (aLearned != bLearned) return@Comparator if (aLearned) -1 else 1
        if (aLearned && aCount != bCount) return@Comparator bCount.compareTo(aCount)
        // 4. 其餘照 boshiamy.cin 原始檔序；同序時短碼在前
        if (a.originalOrder != b.originalOrder) {
            return@Comparator a.originalOrder.compareTo(b.originalOrder)
        }
        a.code.length.compareTo(b.code.length)
    }
}
