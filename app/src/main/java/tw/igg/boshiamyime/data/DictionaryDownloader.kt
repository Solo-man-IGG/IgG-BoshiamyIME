package tw.igg.boshiamyime.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.igg.boshiamyime.model.DictionaryEntry
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DictionaryDownloader(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryDownloader"
        private const val CIN_URL = "https://raw.githubusercontent.com/chinese-opendesktop/cin-tables/master/boshiamy.cin"
        private const val PREFS_NAME = "boshiamy_dict"
        private const val KEY_VERSION = "dict_version"
        private const val KEY_ENTRY_COUNT = "dict_entry_count"
    }

    interface DownloadCallback {
        fun onProgress(progress: Int)
        fun onSuccess(entryCount: Int, version: String)
        fun onError(error: String)
    }

    suspend fun downloadDictionary(callback: DownloadCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress(10)

            val url = URL(CIN_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                callback.onError("HTTP 錯誤：$responseCode")
                return@withContext
            }

            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val cinContent = reader.readText()
            reader.close()
            connection.disconnect()

            callback.onProgress(50)

            val entries = parseCinFile(cinContent)
            callback.onProgress(70)

            val version = "1.0.0-${System.currentTimeMillis()}"
            saveDictionary(entries, version)
            callback.onProgress(100)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_VERSION, version)
                putInt(KEY_ENTRY_COUNT, entries.size)
            }

            callback.onSuccess(entries.size, version)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            callback.onError("下載失敗：${e.message}")
        }
    }

    private fun parseCinFile(cinContent: String): List<DictionaryEntry> {
        val entries = mutableListOf<DictionaryEntry>()
        val seen = mutableSetOf<String>()

        val t9Map = mapOf(
            'a' to "2", 'b' to "2", 'c' to "2",
            'd' to "3", 'e' to "3", 'f' to "3",
            'g' to "4", 'h' to "4", 'i' to "4",
            'j' to "5", 'k' to "5", 'l' to "5",
            'm' to "6", 'n' to "6", 'o' to "6",
            'p' to "7", 'q' to "7", 'r' to "7", 's' to "7",
            't' to "8", 'u' to "8", 'v' to "8",
            'w' to "9", 'x' to "9", 'y' to "9", 'z' to "9"
        )

        var inChardef = false
        for (line in cinContent.lines()) {
            val trimmed = line.trim()
            if (trimmed == "%chardef begin") {
                inChardef = true
                continue
            }
            if (trimmed == "%chardef end") break
            if (!inChardef || trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val parts = trimmed.split("\\s+".toRegex(), limit = 2)
            if (parts.size != 2) continue

            val code = parts[0].lowercase()
            val char = parts[1]

            if (!Regex("^[a-z,.'\\[\\]]+$").matches(code)) continue
            if (code.isEmpty()) continue

            val key = "$code|$char"
            if (key in seen) continue
            seen.add(key)

            val t9 = code.map { t9Map[it] ?: it }.joinToString("")

            entries.add(DictionaryEntry(
                code = code,
                t9 = t9,
                char = char,
                frequency = 0
            ))
        }

        return entries.sortedBy { it.code }
    }

    private fun saveDictionary(entries: List<DictionaryEntry>, version: String) {
        val root = JSONObject()
        root.put("version", version)
        root.put("encoding", "boshiamy-standard")
        root.put("source", "https://github.com/chinese-opendesktop/cin-tables")
        root.put("source_file", "boshiamy.cin")

        val entriesArray = org.json.JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("code", entry.code)
            obj.put("t9", entry.t9)
            obj.put("char", entry.char)
            obj.put("frequency", entry.frequency)
            entriesArray.put(obj)
        }
        root.put("entries", entriesArray)

        val json = root.toString()
        context.openFileOutput("dictionary.json", Context.MODE_PRIVATE).use { fos ->
            fos.write(json.toByteArray(Charsets.UTF_8))
        }
    }

    fun getLocalVersion(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_VERSION, "內建版") ?: "內建版"
    }

    fun getLocalEntryCount(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ENTRY_COUNT, 0)
    }
}
