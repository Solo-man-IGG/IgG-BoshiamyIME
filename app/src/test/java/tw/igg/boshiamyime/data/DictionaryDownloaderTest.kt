package tw.igg.boshiamyime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tw.igg.boshiamyime.model.DictionaryEntry
import java.io.File

/**
 * 驗證下載器套用的字頻邏輯與 tools/build-dictionary.py 的 static_frequency() 完全一致。
 *
 * 兩邊若不一致，下載版字典的候選排序會與 build script 產出的內建版不同，
 * 使用者會看到「同一個碼，下載後排序亂掉」。預期值由 build-dictionary.py 實際算出。
 */
class DictionaryDownloaderTest {

    private fun dataFile(name: String): File {
        val candidates = listOf(
            File("../tools/data/$name"),
            File("tools/data/$name"),
            File("../../tools/data/$name")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("找不到 $name，試過：${candidates.map { it.path }}")
    }

    private fun loadCounts(): Map<String, Int> =
        DictionaryDownloader.parseCharCount(dataFile("moe-char-count.tsv").readText())

    private fun loadSimplified(): Set<String> =
        DictionaryDownloader.parseSimplifiedSet(dataFile("simplified-only.txt").readText())

    @Test
    fun 字頻表解析出完整筆數() {
        assertEquals(5702, loadCounts().size)
        assertEquals(32739, loadCounts()["的"])
    }

    @Test
    fun 簡體字集解析出完整筆數() {
        assertEquals(3809, loadSimplified().size)
        assertTrue("个" in loadSimplified())
    }

    @Test
    fun 註解行不會被當成資料() {
        // 兩份檔案的開頭都是 # 註解，若沒跳過會污染字頻表。
        val counts = loadCounts()
        assertTrue(counts.keys.none { it.startsWith("#") })
        assertTrue(loadSimplified().none { it.startsWith("#") })
    }

    @Test
    fun 字頻與簡體降權和buildScript一致() {
        val counts = loadCounts()
        val simplified = loadSimplified()

        // 預期值由 tools/build-dictionary.py 的 static_frequency() 實際算出。
        val expected = mapOf(
            "的" to 999,          // 最常用字，壓縮後滿值
            "不" to 971,
            "一" to 963,
            "了" to 908,
            "嗎" to 636,
            "國" to 891,
            "個" to 834,
            "龍" to 641,
            "龜" to 389,          // 罕見但有在字頻表內
            "産" to 0,            // 日文異體字，不在教育部字頻表
            "の" to 0,            // 假名，不在字頻表
            "々" to 0,
            "国" to -1000000,     // 簡體：壓到負數區
            "个" to -1000000,
            "龙" to -1000000,
            "麽" to -1000000,
            "么" to -999769       // 簡體但有頻次：保留量體
        )

        val entries = expected.keys.map {
            DictionaryEntry(code = "x", t9 = "9", char = it, frequency = 0)
        }
        val result = DictionaryDownloader.applyFrequency(entries, counts, simplified)
            .associateBy { it.char }

        for ((char, want) in expected) {
            assertEquals("字 '$char' 的頻率", want, result[char]?.frequency)
        }
    }

    @Test
    fun 簡體字一定排在正體之後() {
        val counts = loadCounts()
        val simplified = loadSimplified()
        val entries = listOf("國", "国", "個", "个", "龍", "龙").map {
            DictionaryEntry(code = "x", t9 = "9", char = it, frequency = 0)
        }
        val result = DictionaryDownloader.applyFrequency(entries, counts, simplified)
            .associateBy { it.char }

        for ((simplifiedChar, traditionalChar) in listOf("国" to "國", "个" to "個", "龙" to "龍")) {
            assertTrue(
                "簡體 $simplifiedChar 的頻率必須小於正體 $traditionalChar",
                result.getValue(simplifiedChar).frequency < result.getValue(traditionalChar).frequency
            )
        }
    }

    @Test
    fun 合併以碼字配對去重而非以字去重() {
        // 兩表取碼哲學不同：あ 在 uniliu 是 ja,、在 boshiamy 是 a,。
        // 若以字去重，a, 會被整筆丟掉；必須以配對去重才能兩套並存。
        val uniliu = """
            %chardef begin
            ja,	あ
            jn,	ん
            %chardef end
        """.trimIndent()
        val boshiamy = """
            %chardef begin
            a,	あ
            n,	ん
            no,	の
            %chardef end
        """.trimIndent()

        val merged = DictionaryDownloader.mergeCins(uniliu, boshiamy)
        fun codesOf(char: String) = merged.filter { it.char == char }.map { it.code }.sorted()

        assertEquals(listOf("a,", "ja,"), codesOf("あ"))
        assertEquals(listOf("jn,", "n,"), codesOf("ん"))
        assertEquals(listOf("no,"), codesOf("の"))
    }

    @Test
    fun 完全相同的配對不會重複出現() {
        val a = "%chardef begin\nja,\tあ\n%chardef end"
        val b = "%chardef begin\nja,\tあ\nno,\tの\n%chardef end"
        val merged = DictionaryDownloader.mergeCins(a, b)
        assertEquals(2, merged.size)
        assertEquals(1, merged.count { it.char == "あ" })
    }

    @Test
    fun 使用者自訂頻率壓得過靜態字頻() {
        // DictionaryManager 以 userFreq * 1000 計算；靜態值上限是 999，
        // 使用者只要選过一次就必須能排到最前面。
        assertTrue(999 * 1000 > loadCounts().size)
        val counts = loadCounts()
        val simplified = loadSimplified()
        val entries = listOf(DictionaryEntry(code = "x", t9 = "9", char = "的", frequency = 0))
        val staticFreq = DictionaryDownloader.applyFrequency(entries, counts, simplified).first().frequency
        assertTrue("使用者選過一次（1000）必須大於靜態最高值", 1 * 1000 > staticFreq)
    }
}
