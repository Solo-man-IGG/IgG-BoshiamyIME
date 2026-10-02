package tw.igg.boshiamyime.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONObject
import tw.igg.boshiamyime.model.DictionaryEntry
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class DictionaryManager(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryManager"
        private const val ASSETS_DIR = "tables"
    }

    /**
     * 索引一律「先在區域變數建好，再整組替換」，且讀寫都在 indexLock 內。
     * 這樣重新載入碼表時，正在打字的執行緒不會讀到清空到一半的索引
     * （舊作法是就地 clear() 再重建，中間會有查詢miss或讀到空集合的空窗）。
     * 替換本身只花幾微秒，所以查詢幾乎不會被阻塞。
     */
    private val indexLock = Any()
    private var allEntries: List<DictionaryEntry> = emptyList()
    private var codeIndex: Map<String, List<DictionaryEntry>> = emptyMap()
    private var charIndex: Map<String, List<DictionaryEntry>> = emptyMap()
    private var t9Index: Map<String, List<DictionaryEntry>> = emptyMap()
    private var codePrefixIndex: Map<String, List<DictionaryEntry>> = emptyMap()
    private var t9PrefixIndex: Map<String, List<DictionaryEntry>> = emptyMap()
    private var associations: Map<String, List<String>> = emptyMap()
    private val usageFrequency = mutableMapOf<String, Int>()
    private val learnedAssociations = mutableMapOf<String, MutableMap<String, Int>>()

    var isLoaded = false
        private set

    fun loadDictionary(tableName: String = "standard") {
        try {
            val localFile = File(context.filesDir, "dictionary.json")
            if (!localFile.exists()) {
                // 碼表已改為首次使用時下載，APK 內不再附帶
                isLoaded = false
                loadAssociations("$ASSETS_DIR/$tableName/associations.json")
                Log.i(TAG, "No dictionary yet, user needs to download it first")
                return
            }
            Log.i(TAG, "Loading from local storage")
            val json = localFile.readText(Charsets.UTF_8)
            parseDictionary(json)
            loadAssociations("$ASSETS_DIR/$tableName/associations.json")
            isLoaded = true
            Log.i(TAG, "Loaded ${allEntries.size} entries, ${associations.size} associations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load dictionary: ${e.message}", e)
        }
    }

    fun loadUsageFrequency(prefs: SharedPreferences) {
        usageFrequency.clear()
        for ((key, value) in prefs.all) {
            if (key.startsWith("freq_")) {
                val char = key.removePrefix("freq_")
                usageFrequency[char] = value as? Int ?: 0
            }
        }
        Log.i(TAG, "Loaded ${usageFrequency.size} usage frequency entries")
    }

    fun getUserFrequency(char: String): Int {
        return usageFrequency[char] ?: 0
    }

    fun recordUsage(char: String) {
        usageFrequency[char] = (usageFrequency[char] ?: 0) + 1
    }

    fun loadLearnedAssociations(prefs: SharedPreferences) {
        learnedAssociations.clear()
        for ((key, value) in prefs.all) {
            if (key.startsWith("assoc_")) {
                val first = key.removePrefix("assoc_")
                val entries = value as? Set<*> ?: continue
                val map = mutableMapOf<String, Int>()
                for (entry in entries) {
                    val s = entry as? String ?: continue
                    val idx = s.indexOf('\u0001')
                    if (idx > 0) {
                        val second = s.substring(0, idx)
                        val count = s.substring(idx + 1).toIntOrNull() ?: 0
                        if (second.isNotEmpty()) map[second] = count
                    }
                }
                if (map.isNotEmpty()) learnedAssociations[first] = map
            }
        }
    }

    fun recordBigram(first: String, second: String) {
        if (first.isEmpty() || second.isEmpty() || first == second) return
        val map = learnedAssociations.getOrPut(first) { mutableMapOf() }
        map[second] = (map[second] ?: 0) + 1
    }

    fun getLearnedAssociationsCount(): Int {
        return learnedAssociations.values.sumOf { it.size }
    }

    fun saveLearnedAssociations(prefs: SharedPreferences) {
        val known = prefs.all.keys.filter { it.startsWith("assoc_") }
        val editor = prefs.edit()
        known.forEach { editor.remove(it) }
        for ((first, map) in learnedAssociations) {
            editor.putStringSet("assoc_$first", map.map { "${it.key}\u0001${it.value}" }.toSet())
        }
        editor.apply()
    }

    /**
     * 重新載入碼表索引（設定頁下載完成後呼叫，讓新碼表立刻生效）。
     * 只換索引，不動 usageFrequency / learnedAssociations ——
     * 使用者學起來的字頻與詞聯不該因為更新碼表而消失。
     * 舊的 reloadDictionary() 會把這兩個清掉而且沒從 prefs 讀回，等於清除學習成果。
     */
    fun reloadDictionary() {
        isLoaded = false
        loadDictionary()
    }

    private fun loadAssetJson(path: String): String {
        context.assets.open(path).use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                return reader.readText()
            }
        }
    }

    /** App 是否已有可用碼表（首次使用需先在設定中下載）。 */
    fun hasDictionary(): Boolean {
        return File(context.filesDir, "dictionary.json").exists()
    }

    private fun parseDictionary(json: String) {
        val newAll = mutableListOf<DictionaryEntry>()
        val newCode = mutableMapOf<String, MutableList<DictionaryEntry>>()
        val newChar = mutableMapOf<String, MutableList<DictionaryEntry>>()
        val newT9 = mutableMapOf<String, MutableList<DictionaryEntry>>()
        val newCodePrefix = mutableMapOf<String, MutableList<DictionaryEntry>>()
        val newT9Prefix = mutableMapOf<String, MutableList<DictionaryEntry>>()

        val root = JSONObject(json)
        val entriesArray = root.getJSONArray("entries")

        for (i in 0 until entriesArray.length()) {
            val obj = entriesArray.getJSONObject(i)
            val entry = DictionaryEntry(
                code = obj.getString("code"),
                t9 = obj.optString("t9", ""),
                char = obj.getString("char"),
                frequency = obj.optInt("frequency", 0),
                phrases = emptyList()
            )

            newAll.add(entry)
            newCode.getOrPut(entry.code) { mutableListOf() }.add(entry)
            newChar.getOrPut(entry.char) { mutableListOf() }.add(entry)
            if (entry.t9.isNotEmpty()) {
                newT9.getOrPut(entry.t9) { mutableListOf() }.add(entry)
                for (len in 1..entry.t9.length) {
                    val prefix = entry.t9.substring(0, len)
                    newT9Prefix.getOrPut(prefix) { mutableListOf() }.add(entry)
                }
            }
            for (len in 1..entry.code.length) {
                val prefix = entry.code.substring(0, len)
                newCodePrefix.getOrPut(prefix) { mutableListOf() }.add(entry)
            }
        }

        synchronized(indexLock) {
            allEntries = newAll
            codeIndex = newCode
            charIndex = newChar
            t9Index = newT9
            codePrefixIndex = newCodePrefix
            t9PrefixIndex = newT9Prefix
        }
    }

    private fun loadAssociations(path: String) {
        try {
            val json = loadAssetJson(path)
            val root = JSONObject(json)
            val assocObj = root.getJSONObject("associations")

            val newAssoc = mutableMapOf<String, List<String>>()
            for (key in assocObj.keys()) {
                val arr = assocObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                newAssoc[key] = list
            }
            synchronized(indexLock) { associations = newAssoc }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load associations: ${e.message}")
        }
    }

    fun lookup(code: String): List<DictionaryEntry> {
        val hit = synchronized(indexLock) { codeIndex[code] }
        return hit?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun lookupPrefix(prefix: String): List<DictionaryEntry> {
        val hit = synchronized(indexLock) { codePrefixIndex[prefix] }
        return hit?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun lookupByChar(char: String): DictionaryEntry? {
        val hit = synchronized(indexLock) { charIndex[char] }
        return hit?.firstOrNull()
    }

    fun lookupT9(t9Code: String): List<DictionaryEntry> {
        val hit = synchronized(indexLock) { t9Index[t9Code] }
        return hit?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun lookupT9Prefix(t9Prefix: String): List<DictionaryEntry> {
        val hit = synchronized(indexLock) { t9PrefixIndex[t9Prefix] }
        return hit?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun getAssociations(char: String): List<String> {
        val static = synchronized(indexLock) { associations[char] } ?: emptyList()
        val learned = learnedAssociations[char]
            ?.entries
            ?.sortedByDescending { it.value }
            ?.map { it.key }
            ?: emptyList()
        return (learned + static).distinct()
    }

    fun getAllEntries(): List<DictionaryEntry> {
        return allEntries.toList()
    }

    fun getEntriesByCode(code: String): List<DictionaryEntry> {
        return codeIndex[code] ?: emptyList()
    }

    fun getEntryCount(): Int = allEntries.size

    private fun getEffectiveFrequency(entry: DictionaryEntry): Int {
        val userFreq = usageFrequency[entry.char] ?: 0
        return entry.frequency + userFreq * 1000
    }

    fun effectiveFrequency(code: String, char: String): Int {
        val userFreq = usageFrequency[char] ?: 0
        val static = synchronized(indexLock) {
            codeIndex[code]?.firstOrNull { it.char == char }?.frequency
        } ?: 0
        return static + userFreq * 1000
    }
}
