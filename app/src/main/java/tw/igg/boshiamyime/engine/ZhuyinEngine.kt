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
        private const val MAX_CANDIDATES = 9

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
    }

    private val entries = mutableMapOf<String, MutableList<ZhuyinEntry>>()
    private val charBopomofo = mutableMapOf<String, MutableSet<String>>()
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
        }
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
            .take(MAX_CANDIDATES)
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
            .take(MAX_CANDIDATES)
    }

    fun isLoaded(): Boolean = loaded

    fun lookupBopomofoByChar(char: String): String {
        return charBopomofo[char]?.sorted()?.joinToString("、") ?: ""
    }
}
