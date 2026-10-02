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

        // 字頻與簡體字集放在本專案 repo，供下載器套用。
        // 放在 GitHub 而非打包進 APK 的理由：字頻可獨立更新，且讓「下載版」
        // 與「內建版」走同一條路徑，不會再出現只有某一版有字頻的不一致。
        private const val DATA_BASE_URL =
            "https://raw.githubusercontent.com/Solo-man-IGG/IgG-BoshiamyIME/master/tools/data/"
        private const val DATA_CHARC_COUNT = "moe-char-count.tsv"
        private const val DATA_SIMPLIFIED = "simplified-only.txt"

        // 與 tools/build-dictionary.py 保持一致：字頻壓縮到 1..999，
        // 使用者自訂頻率 * 1000 一定壓得過靜態值。
        private const val STATIC_MAX = 999
        private const val SIMPLIFIED_PENALTY = 1_000_000

        /**
         * 解析教育部字頻表（tools/data/moe-char-count.tsv）。
         * 格式：以 # 開頭為註解，資料行為「字元<TAB>原始出現頻次」。
         */
        internal fun parseCharCount(content: String): Map<String, Int> {
            val counts = HashMap<String, Int>(8192)
            for (line in content.lineSequence()) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
                val tab = trimmed.indexOf('\t')
                if (tab <= 0) continue
                val char = trimmed.substring(0, tab)
                val count = trimmed.substring(tab + 1).trim().toIntOrNull() ?: continue
                // 用 codePointCount 判斷「單一字元」：Kotlin 的 String.length 算 UTF-16
                // 單位，倉頡補集的罕見字（U+20000 以上）會算成 2 而被誤判跳過。
                if (isSingleChar(char) && count > 0) counts[char] = count
            }
            return counts
        }

        /** 解析簡體降權字集（tools/data/simplified-only.txt），每行一字。 */
        internal fun parseSimplifiedSet(content: String): Set<String> {
            val chars = HashSet<String>(8192)
            for (line in content.lineSequence()) {
                val char = line.trim()
                if (char.isEmpty() || char.startsWith("#")) continue
                if (isSingleChar(char)) chars.add(char)
            }
            return chars
        }

        private fun isSingleChar(s: String): Boolean =
            s.isNotEmpty() && s.codePointCount(0, s.length) == 1

        /**
         * 套用字頻與簡體降權，與 tools/build-dictionary.py 的 static_frequency() 一致：
         * 原始頻次取對數壓縮到 1..999；不在字頻表的罕見/異體字給 0。
         * 簡體字保留頻次量體但整體壓到負數區，確保一定排在正體後面。
         */
        internal fun applyFrequency(
            entries: List<DictionaryEntry>,
            counts: Map<String, Int>,
            simplified: Set<String>
        ): List<DictionaryEntry> {
            val logTop = Math.log(counts.values.max().toDouble())
            val result = entries.map { e ->
                val count = counts[e.char]
                var freq = if (count == null || count <= 0) 0 else {
                    1 + Math.round(
                        (STATIC_MAX - 1) * Math.log(count.toDouble()) / logTop
                    ).toInt()
                }
                if (e.char in simplified) {
                    freq = -SIMPLIFIED_PENALTY + Math.abs(freq)
                }
                if (e.frequency != freq) e.copy(frequency = freq) else e
            }
            return result
        }
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

            val primary = fetchUrl(CIN_BASE_URL + CIN_PRIMARY)
                ?: return@withContext callback.onError("下載 $CIN_PRIMARY 失敗，請檢查網路")
            callback.onProgress(30)

            val supplement = fetchUrl(CIN_BASE_URL + CIN_SUPPLEMENT)
                ?: return@withContext callback.onError("下載 $CIN_SUPPLEMENT 失敗，請檢查網路")
            callback.onProgress(50)

            val charCount = fetchUrl(DATA_BASE_URL + DATA_CHARC_COUNT)
            val simplified = fetchUrl(DATA_BASE_URL + DATA_SIMPLIFIED)
            if (charCount != null && simplified != null) {
                callback.onProgress(65)
            }
            val merged = mergeCins(primary, supplement)
            callback.onProgress(80)

            // 字頻與簡體降權是排序正確性的關鍵；缺了會讓所有候選字頻率為 0，
            // 退化回「碼短優先」的舊排序。因此拿不到字頻時直接中止，不寫入殘缺字典。
            if (charCount == null || simplified == null) {
                callback.onError("下載字頻資料失敗，請檢查網路後重試")
                return@withContext
            }

            val counts = parseCharCount(charCount)
            val simplifiedChars = parseSimplifiedSet(simplified)
            if (counts.isEmpty() || simplifiedChars.isEmpty()) {
                callback.onError("字頻資料格式異常，已中止（避免排序退化）")
                return@withContext
            }
            Log.i(TAG, "字頻 ${counts.size} 字、簡體 ${simplifiedChars.size} 字")

            val entries = applyFrequency(merged, counts, simplifiedChars)
            callback.onProgress(92)

            val version = buildVersion(entries, counts)
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

    private fun fetchUrl(url: String): String? {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.connect()
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP ${connection.responseCode} for $url")
                connection.disconnect()
                return null
            }
            val content = connection.inputStream.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
            connection.disconnect()
            content
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch $url", e)
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

    /**
     * 版本字串以「筆數 + 內容雜湊」組成。
     * 內容雜湊讓上游更新碼表時版本號必然改變，之後要判斷「有新版可用」
     * 只需比對這個字串，不必另存下載時間戳。
     */
    private fun buildVersion(entries: List<DictionaryEntry>, counts: Map<String, Int>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val payload = buildString {
            append(entries.size).append(':').append(counts.size).append(':')
            append(DATA_CHARC_COUNT).append(':').append(DATA_SIMPLIFIED).append('\n')
            for (e in entries) {
                append(e.code).append('|').append(e.char).append('|')
                .append(e.frequency).append('\n')
            }
        }
        val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return "${entries.size}字-$hash"
    }

    private fun saveDictionary(entries: List<DictionaryEntry>, version: String) {
        val root = JSONObject()
        root.put("version", version)
        root.put("encoding", "boshiamy-standard")
        root.put("source", "https://github.com/chinese-opendesktop/cin-tables")
        root.put("source_file", "$CIN_PRIMARY + $CIN_SUPPLEMENT")
        root.put("frequency_source", "$DATA_BASE_URL$DATA_CHARC_COUNT")

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
