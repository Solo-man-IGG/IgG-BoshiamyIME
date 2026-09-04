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

    private val allEntries = mutableListOf<DictionaryEntry>()
    private val codeIndex = mutableMapOf<String, MutableList<DictionaryEntry>>()
    private val charIndex = mutableMapOf<String, MutableList<DictionaryEntry>>()
    private val t9Index = mutableMapOf<String, MutableList<DictionaryEntry>>()
    private val associations = mutableMapOf<String, List<String>>()
    private val usageFrequency = mutableMapOf<String, Int>()
    private val learnedAssociations = mutableMapOf<String, MutableMap<String, Int>>()

    var isLoaded = false
        private set

    fun loadDictionary(tableName: String = "standard") {
        try {
            val localFile = File(context.filesDir, "dictionary.json")
            val json = if (localFile.exists()) {
                Log.i(TAG, "Loading from local storage")
                localFile.readText(Charsets.UTF_8)
            } else {
                Log.i(TAG, "Loading from assets: $ASSETS_DIR/$tableName/dictionary.json")
                loadAssetJson("$ASSETS_DIR/$tableName/dictionary.json")
            }
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

    fun reloadDictionary() {
        allEntries.clear()
        codeIndex.clear()
        charIndex.clear()
        t9Index.clear()
        associations.clear()
        usageFrequency.clear()
        learnedAssociations.clear()
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

    private fun parseDictionary(json: String) {
        allEntries.clear()
        codeIndex.clear()
        charIndex.clear()
        t9Index.clear()

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

            allEntries.add(entry)
            codeIndex.getOrPut(entry.code) { mutableListOf() }.add(entry)
            charIndex.getOrPut(entry.char) { mutableListOf() }.add(entry)
            if (entry.t9.isNotEmpty()) {
                t9Index.getOrPut(entry.t9) { mutableListOf() }.add(entry)
            }
        }
    }

    private fun loadAssociations(path: String) {
        try {
            val json = loadAssetJson(path)
            val root = JSONObject(json)
            val assocObj = root.getJSONObject("associations")

            for (key in assocObj.keys()) {
                val arr = assocObj.getJSONArray(key)
                val list = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    list.add(arr.getString(i))
                }
                associations[key] = list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load associations: ${e.message}")
        }
    }

    fun lookup(code: String): List<DictionaryEntry> {
        return codeIndex[code]?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun lookupPrefix(prefix: String): List<DictionaryEntry> {
        return allEntries
            .filter { it.code.startsWith(prefix) }
            .sortedByDescending { getEffectiveFrequency(it) }
    }

    fun lookupByChar(char: String): DictionaryEntry? {
        return charIndex[char]?.firstOrNull()
    }

    fun lookupT9(t9Code: String): List<DictionaryEntry> {
        return t9Index[t9Code]?.sortedByDescending { getEffectiveFrequency(it) } ?: emptyList()
    }

    fun getAssociations(char: String): List<String> {
        val static = associations[char] ?: emptyList()
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
        val static = codeIndex[code]?.firstOrNull { it.char == char }?.frequency ?: 0
        return static + userFreq * 1000
    }
}
