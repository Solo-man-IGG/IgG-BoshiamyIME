package tw.igg.boshiamyime.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdater"
        private const val RELEASE_API =
            "https://api.github.com/repos/Solo-man-IGG/IgG-BoshiamyIME/releases/latest"
        private const val USER_AGENT = "BoshiamyIME-Updater"
    }

    data class UpdateCheckResult(
        val updateAvailable: Boolean,
        val latestVersion: String?,
        val apkUrl: String?,
        val apkName: String?,
        val error: String?
    )

    suspend fun checkUpdate(currentVersion: String): UpdateCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val connection = URL(RELEASE_API).openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 30000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", USER_AGENT)

                val responseCode = connection.responseCode
                val body = connection.inputStream
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                connection.disconnect()

                if (responseCode != HttpURLConnection.HTTP_OK || body == null) {
                    return@withContext UpdateCheckResult(
                        false, null, null, null, "HTTP 錯誤：$responseCode"
                    )
                }

                val json = JSONObject(body)
                val latest = json.optString("tag_name", "").removePrefix("v")
                val assets = json.optJSONArray("assets")
                if (latest.isEmpty() || assets == null) {
                    return@withContext UpdateCheckResult(false, null, null, null, "無法讀取版本資訊")
                }

                var apkUrl: String? = null
                var apkName: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name")
                    if (name.endsWith("-release.apk")) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        break
                    }
                }
                if (apkUrl.isNullOrEmpty()) {
                    return@withContext UpdateCheckResult(false, latest, null, null, null)
                }

                UpdateCheckResult(isNewer(latest, currentVersion), latest, apkUrl, apkName, null)
            } catch (e: Exception) {
                Log.e(TAG, "checkUpdate failed", e)
                UpdateCheckResult(false, null, null, null, e.message)
            }
        }

    suspend fun downloadApk(url: String, apkName: String): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val dest = File(dir, apkName)
            dest.delete()

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 60000
            connection.setRequestProperty("User-Agent", USER_AGENT)

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return@withContext null
            }
            connection.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            if (dest.length() > 0) dest else null
        } catch (e: Exception) {
            Log.e(TAG, "downloadApk failed", e)
            null
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').mapNotNull { it.toIntOrNull() }
        val b = current.split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}