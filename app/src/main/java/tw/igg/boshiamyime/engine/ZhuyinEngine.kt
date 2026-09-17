package tw.igg.boshiamyime.engine

import android.content.Context
import android.util.Log
import org.json.JSONObject
import tw.igg.boshiamyime.model.Candidate
import java.io.BufferedReader
import java.io.InputStreamReader

class ZhuyinEngine(private val context: Context) {

    companion object {
        private const val TAG = "ZhuyinEngine"

        val KEY_TO_BOPOMOFO = mapOf(
            ',' to "ㄝ", '-' to "ㄦ", '.' to "ㄡ", '/' to "ㄥ",
            '0' to "ㄢ", '1' to "ㄅ", '2' to "ㄉ", '3' to "ˇ",
            '4' to "ˋ", '5' to "ㄓ", '6' to "ˊ", '7' to "˙",
            '8' to "ㄚ", '9' to "ㄞ", ';' to "ㄤ", 'a' to "ㄇ",
            'b' to "ㄖ", 'c' to "ㄏ", 'd' to "ㄎ", 'e' to "ㄍ",
            'f' to "ㄑ", 'g' to "ㄕ", 'h' to "ㄘ", 'i' to "ㄛ",
            'j' to "ㄨ", 'k' to "ㄜ", 'l' to "ㄠ", 'm' to "ㄩ",
            'n' to "ㄙ", 'o' to "ㄟ", 'p' to "ㄣ", 'q' to "ㄆ",
            'r' to "ㄐ", 's' to "ㄋ", 't' to "ㄔ", 'u' to "ㄧ",
            'v' to "ㄒ", 'w' to "ㄊ", 'x' to "ㄌ", 'y' to "ㄗ",
            'z' to "ㄈ"
        )

        private val TONE_KEYS = setOf('3', '4', '6', '7')

        val BOPOMOFO_TO_KEY: Map<String, String> by lazy {
            buildMap {
                KEY_TO_BOPOMOFO.forEach { (key, symbol) ->
                    put(symbol, key.toString())
                }
            }
        }

        private const val TOP_N = 6
        private const val MAX_PHRASES = 6
        private const val BIGRAM_BONUS = 3000
    }

    private val entries = mutableMapOf<String, MutableList<ZhuyinEntry>>()
    private val charBopomofo = mutableMapOf<String, MutableSet<String>>()
    private val syllPrefixTop = mutableMapOf<String, MutableList<Pair<String, Int>>>()
    private var loaded = false

    data class ZhuyinEntry(
        val code: String,
        val char: String,
        val bopomofo: String,
        val frequency: Int
    )

    fun loadDictionary() {
        try {
            val json = loadAssetJson("tables/zhuyin/dictionary.json")
            parseDictionary(json)
            loaded = true
            Log.i(TAG, "Loaded ${entries.size} Zhuyin entries")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load: ${e.message}")
        }
    }

    private fun loadAssetJson(path: String): String {
        context.assets.open(path).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                return reader.readText()
            }
        }
    }

    private fun parseDictionary(json: String) {
        entries.clear()
        charBopomofo.clear()
        syllPrefixTop.clear()
        val root = JSONObject(json)
        val entriesArray = root.getJSONArray("entries")

        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.getJSONObject(i)
            val code = obj.getString("code")
            val char = obj.getString("char")
            val bopomofo = obj.optString("bopomofo", "")
            val frequency = obj.optInt("frequency", 0)

            val entry = ZhuyinEntry(
                code = code,
                char = char,
                bopomofo = bopomofo,
                frequency = frequency
            )
            entries.getOrPut(code) { mutableListOf() }.add(entry)
            if (bopomofo.isNotEmpty()) {
                charBopomofo.getOrPut(char) { mutableSetOf() }.add(bopomofo)
            }
            for (len in 1..code.length) {
                insertSyllPrefix(code.substring(0, len), char, frequency)
            }
        }
    }

    private fun insertSyllPrefix(prefix: String, char: String, frequency: Int) {
        val list = syllPrefixTop.getOrPut(prefix) { mutableListOf() }
        val existing = list.indexOfFirst { it.first == char }
        if (existing >= 0) {
            if (frequency > list[existing].second) {
                list[existing] = char to frequency
                list.sortByDescending { it.second }
            }
            return
        }
        val insertAt = list.indexOfFirst { it.second < frequency }
        if (insertAt >= 0) list.add(insertAt, char to frequency) else list.add(char to frequency)
        if (list.size > TOP_N) list.removeAt(list.size - 1)
    }

    fun codeToBopomofo(code: String): String {
        return code.map { KEY_TO_BOPOMOFO[it] ?: it }.joinToString("")
    }

    fun symbolToCode(symbol: String): String {
        return BOPOMOFO_TO_KEY[symbol] ?: symbol
    }

    fun isToneKey(key: String): Boolean {
        return key.length == 1 && key[0] in TONE_KEYS
    }

    fun lookup(code: String): List<Candidate> {
        if (!loaded) return emptyList()

        val seen = mutableSetOf<String>()
        return (entries[code] ?: emptyList())
            .sortedByDescending { it.frequency }
            .map { Candidate(code = it.code, char = it.char, frequency = it.frequency) }
            .filter { seen.add(it.char) }
    }

    fun lookupPrefix(prefix: String): List<Candidate> {
        if (!loaded) return emptyList()

        val seen = mutableSetOf<String>()
        return entries.keys
            .filter { it.startsWith(prefix) }
            .flatMap { code -> entries[code] ?: emptyList() }
            .sortedByDescending { it.frequency }
            .map { Candidate(code = it.code, char = it.char, frequency = it.frequency) }
            .filter { seen.add(it.char) }
    }

    fun lookupPhrase(keys: String, bigram: (String, String) -> Int = { _, _ -> 0 }): List<Candidate> {
        if (!loaded || keys.length < 3) return emptyList()

        val results = linkedMapOf<String, Int>()
        for (len1 in 1 until keys.length) {
            val s1 = keys.substring(0, len1)
            val s2 = keys.substring(len1)
            if (s2.isEmpty()) continue
            val c1 = syllPrefixTop[s1] ?: continue
            val c2 = syllPrefixTop[s2] ?: continue
            for ((a, fa) in c1) {
                for ((b, fb) in c2) {
                    if (a == b) continue
                    val phrase = a + b
                    val bonus = if (bigram(a, b) > 0) BIGRAM_BONUS else 0
                    val score = fa + fb + bonus
                    val prev = results[phrase]
                    if (prev == null || score > prev) results[phrase] = score
                }
            }
        }

        return results.entries
            .sortedByDescending { it.value }
            .take(MAX_PHRASES)
            .map { Candidate(code = keys, char = it.key, frequency = it.value) }
    }

    fun isLoaded(): Boolean = loaded
}
