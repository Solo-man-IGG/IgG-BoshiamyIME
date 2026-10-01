package tw.igg.boshiamyime.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import tw.igg.boshiamyime.model.DictionaryEntry
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class DictionaryDownloader(private val context: Context) {

    companion object {
        private const val TAG = "DictionaryDownloader"
        private const val CIN_BASE_URL =
            "https://raw.githubusercontent.com/chinese-opendesktop/cin-tables/master/"
        private const val CIN_PRIMARY = "uniliu.cin"
        private const val CIN_SUPPLEMENT = "boshiamy.cin"
        private const val PREFS_NAME = "boshiamy_dict"
        private const val KEY_VERSION = "dict_version"
        private const val KEY_ENTRY_COUNT = "dict_entry_count"
        private const val KEY_SOURCE_FILE = "dict_source_file"
    }

    interface DownloadCallback {
        fun onProgress(progress: Int)
        fun onSuccess(entryCount: Int, version: String)
        fun onError(error: String)
    }

    suspend fun downloadDictionary(callback: DownloadCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress(5)

            val primary = fetchCin(CIN_PRIMARY)
                ?: return@withContext callback.onError("下載 $CIN_PRIMARY 失敗，請檢查網路")
            callback.onProgress(40)

            val supplement = fetchCin(CIN_SUPPLEMENT)
                ?: return@withContext callback.onError("下載 $CIN_SUPPLEMENT 失敗，請檢查網路")
            callback.onProgress(65)

            val entries = mergeCins(primary, supplement)
            callback.onProgress(85)

            val version = "merged-${System.currentTimeMillis()}"
            saveDictionary(entries, version)
            callback.onProgress(100)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_VERSION, version)
                putInt(KEY_ENTRY_COUNT, entries.size)
                putString(KEY_SOURCE_FILE, "$CIN_PRIMARY + $CIN_SUPPLEMENT")
            }

            callback.onSuccess(entries.size, version)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            callback.onError("下載失敗：${e.message}")
        }
    }

    private fun fetchCin(fileName: String): String? {
        return try {
            val connection = URL(CIN_BASE_URL + fileName).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $fileName")
                connection.disconnect()
                return null
            }
            val content = connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            connection.disconnect()
            content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch $fileName", e)
            null
        }
    }

    /**
     * 合併兩份碼表：以 uniliu.cin（萬國蝦米）為主，boshiamy.cin（原廠嘸蝦米）
     * 只補前者沒有的字。兩表的漢字覆蓋幾乎重疊，差異是原廠多出 42 個字
     * （№ ①② ⅰⅱ 羅馬數字，以及日文會用到的 々 〆 ゞ ヂ ヅ ヴ ヾ），
     * 而萬國蝦米獨缺「の」。合併後兩邊的缺字都補齊。
     */
    private fun mergeCins(primary: String, supplement: String): List<DictionaryEntry> {
        val main = parseCinFile(primary)
        val existingChars = HashSet<String>(main.size * 2)
        for (e in main) existingChars.add(e.char)

        val extra = parseCinFile(supplement).filter { existingChars.add(it.char) }
        Log.i(TAG, "mergeCins: ${main.size} + ${extra.size} = ${main.size + extra.size}")
        return (main + extra).sortedBy { it.code }
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
        root.put("source_file", "uniliu.cin")

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

    /**
     * 下載的字典會覆蓋內建版（DictionaryManager 優先讀 filesDir/dictionary.json），
     * 但下載來源 uniliu.cin 本身缺「の」等字，且沒有字頻。
     * 使用者可在此清除下載版，改用 App 內建的字典。
     */
    fun hasDownloadedDictionary(): Boolean = localDictionaryFile().exists()

    fun getDownloadedSourceFile(): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOURCE_FILE, "") ?: ""
    }

    private fun localDictionaryFile() = File(context.filesDir, "dictionary.json")
}
