package tw.igg.boshiamyime

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import tw.igg.boshiamyime.data.AppUpdater
import tw.igg.boshiamyime.data.DictionaryDownloader
import tw.igg.boshiamyime.theme.ThemePalette
import tw.igg.boshiamyime.ui.KeyboardPreviewView
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var downloader: DictionaryDownloader
    private lateinit var appUpdater: AppUpdater

    private lateinit var tvAppVersion: TextView
    private lateinit var btnCheckUpdate: Button

    private lateinit var preview: KeyboardPreviewView
    private lateinit var customThemeContainer: View

    private lateinit var btnThemeBg: Button
    private lateinit var btnThemeText: Button
    private lateinit var btnThemeKeyBg: Button
    private lateinit var btnThemeBorder: Button
    private lateinit var etThemeBg: EditText
    private lateinit var etThemeText: EditText
    private lateinit var etThemeKeyBg: EditText
    private lateinit var etThemeBorder: EditText

    private lateinit var tvDictVersion: TextView
    private lateinit var tvDictCount: TextView
    private lateinit var btnUpdate: Button
    private lateinit var progressUpdate: ProgressBar
    private lateinit var tvUpdateStatus: TextView

    private val allowedPrefKeys = listOf(
        "default_input_mode", "keyboard_scale", "delete_key_location",
        "vibrate", "vibrate_strength", "sound", "full_width",
        "theme_mode",
        "theme_bg", "theme_text", "theme_key_bg", "theme_key_pressed", "theme_border"
    )

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) exportSettings(uri)
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) importSettings(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = getSharedPreferences("boshiamy_prefs", MODE_PRIVATE)
        downloader = DictionaryDownloader(this)

        preview = findViewById(R.id.preview_keyboard)
        customThemeContainer = findViewById(R.id.custom_theme_container)

        btnThemeBg = findViewById(R.id.btn_theme_bg)
        btnThemeText = findViewById(R.id.btn_theme_text)
        btnThemeKeyBg = findViewById(R.id.btn_theme_keybg)
        btnThemeBorder = findViewById(R.id.btn_theme_border)
        etThemeBg = findViewById(R.id.et_theme_bg)
        etThemeText = findViewById(R.id.et_theme_text)
        etThemeKeyBg = findViewById(R.id.et_theme_keybg)
        etThemeBorder = findViewById(R.id.et_theme_border)

        tvDictVersion = findViewById(R.id.tv_dict_version)
        tvDictCount = findViewById(R.id.tv_dict_count)
        btnUpdate = findViewById(R.id.btn_update_dict)
        progressUpdate = findViewById(R.id.progress_update)
        tvUpdateStatus = findViewById(R.id.tv_update_status)

        setupInputModeSpinner()
        setupKeyboardScaleSpinner()
        setupDeleteKeySpinner()
        setupVibrateControls()
        setupSwitch(R.id.switch_sound, "sound", false)
        setupSwitch(R.id.switch_full_width, "full_width", false)

        setupThemeModeSpinner()
        setupThemeControls()
        setupDictManagement()
        setupDataButtons()
        setupUpdateChecker()

        updateThemeUi()
        refreshPreview()
    }

    private fun setupSpinner(
        spinnerId: Int,
        options: Array<String>,
        values: Array<String>,
        saved: String,
        onSelected: (String) -> Unit
    ) {
        val spinner = findViewById<Spinner>(spinnerId)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, options
        )
        val index = values.indexOfFirst { it == saved }.coerceAtLeast(0)
        spinner.setSelection(index)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (position in values.indices) onSelected(values[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupInputModeSpinner() {
        setupSpinner(
            R.id.spinner_input_mode,
            resources.getStringArray(R.array.input_mode_options),
            resources.getStringArray(R.array.input_mode_values),
            prefs.getString("default_input_mode", "T9") ?: "T9"
        ) { value ->
            prefs.edit().putString("default_input_mode", value).apply()
        }
    }

    private fun setupKeyboardScaleSpinner() {
        setupSpinner(
            R.id.spinner_keyboard_scale,
            resources.getStringArray(R.array.keyboard_scale_options),
            resources.getStringArray(R.array.keyboard_scale_values),
            prefs.getFloat("keyboard_scale", 1.0f).toString()
        ) { value ->
            prefs.edit().putFloat("keyboard_scale", value.toFloatOrNull() ?: 1.0f).apply()
        }
    }

    private fun setupDeleteKeySpinner() {
        setupSpinner(
            R.id.spinner_delete_key,
            resources.getStringArray(R.array.delete_key_options),
            resources.getStringArray(R.array.delete_key_values),
            prefs.getString("delete_key_location", "enter") ?: "enter"
        ) { value ->
            prefs.edit().putString("delete_key_location", value).apply()
        }
    }

    private fun setupThemeModeSpinner() {
        setupSpinner(
            R.id.spinner_theme_mode,
            resources.getStringArray(R.array.theme_mode_options),
            resources.getStringArray(R.array.theme_mode_values),
            prefs.getString("theme_mode", ThemePalette.MODE_SYSTEM) ?: ThemePalette.MODE_SYSTEM
        ) { value ->
            prefs.edit().putString("theme_mode", value).apply()
            updateThemeUi()
            refreshPreview()
        }
    }

    private fun setupSwitch(viewId: Int, key: String, default: Boolean) {
        val sw = findViewById<Switch>(viewId)
        sw.isChecked = prefs.getBoolean(key, default)
        sw.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }

    private fun setupVibrateControls() {
        val switchVibrate = findViewById<Switch>(R.id.switch_vibrate)
        val strengthRow = findViewById<View>(R.id.vibrate_strength_row)
        val seekBar = findViewById<SeekBar>(R.id.seek_vibrate_strength)
        val label = findViewById<TextView>(R.id.tv_vibrate_strength)

        switchVibrate.isChecked = prefs.getBoolean("vibrate", true)
        switchVibrate.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("vibrate", isChecked).apply()
            strengthRow.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        val saved = prefs.getInt("vibrate_strength", 50).coerceIn(0, 100)
        seekBar.progress = saved
        label.text = saved.toString()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                prefs.edit().putInt("vibrate_strength", seekBar?.progress ?: 50).apply()
            }
        })

        strengthRow.visibility = if (switchVibrate.isChecked) View.VISIBLE else View.GONE
    }

    private fun applyPreview(btn: Button, color: Int) {
        btn.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 8f
        }
    }

    private fun toHex(color: Int): String =
        String.format(Locale.US, "#%06X", color and 0xFFFFFF)

    private fun parseHex(text: String): Int? {
        val trimmed = text.trim().removePrefix("#")
        return try {
            if (trimmed.length == 6) Color.parseColor("#$trimmed") else null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun setupThemeControls() {
        fun bindSwitchButton(
            key: String,
            btn: Button,
            et: EditText,
            defaultColor: Int
        ) {
            fun applyColor(color: Int) {
                prefs.edit().putInt(key, color).apply()
                applyPreview(btn, color)
                et.setText(toHex(color))
                refreshPreview()
            }
            btn.setOnClickListener {
                showColorPicker(key, defaultColor) { color -> applyColor(color) }
            }
            et.setOnEditorActionListener { _, actionId, event ->
                val done = actionId == EditorInfo.IME_ACTION_DONE ||
                    (event?.action == KeyEvent.ACTION_DOWN &&
                        event.keyCode == KeyEvent.KEYCODE_ENTER)
                if (done) {
                    applyHexFromField(key, et)
                    true
                } else {
                    false
                }
            }
            et.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) applyHexFromField(key, et)
            }
        }

        bindSwitchButton("theme_bg", btnThemeBg, etThemeBg, ThemePalette.light().bg)
        bindSwitchButton("theme_text", btnThemeText, etThemeText, ThemePalette.light().text)
        bindSwitchButton("theme_key_bg", btnThemeKeyBg, etThemeKeyBg, ThemePalette.light().keyBg)
        bindSwitchButton("theme_border", btnThemeBorder, etThemeBorder, ThemePalette.light().border)

        findViewById<Button>(R.id.btn_theme_reset).setOnClickListener {
            prefs.edit()
                .remove("theme_bg")
                .remove("theme_text")
                .remove("theme_key_bg")
                .remove("theme_key_pressed")
                .remove("theme_border")
                .apply()
            updateThemeUi()
            refreshPreview()
            Toast.makeText(this, "已恢復預設色彩", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyHexFromField(key: String, et: EditText) {
        val color = parseHex(et.text.toString())
        if (color != null) {
            prefs.edit().putInt(key, color).apply()
            val btn = when (key) {
                "theme_bg" -> btnThemeBg
                "theme_text" -> btnThemeText
                "theme_key_bg" -> btnThemeKeyBg
                else -> btnThemeBorder
            }
            applyPreview(btn, color)
            et.setText(toHex(color))
            refreshPreview()
        } else {
            Toast.makeText(this, "色碼格式錯誤，請輸入 #RRGGBB", Toast.LENGTH_SHORT).show()
            updateThemeUi()
        }
    }

    private fun showColorPicker(key: String, defaultColor: Int, onSelected: (Int) -> Unit) {
        val preset = when (key) {
            "theme_bg" -> intArrayOf(
                0xFFF1F3F4.toInt(), 0xFF2B2B2B.toInt(), 0xFF1A1A2E.toInt(),
                0xFF0F3460.toInt(), 0xFF16213E.toInt(), 0xFF533483.toInt()
            )
            "theme_text" -> intArrayOf(
                0xFF202124.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A73E8.toInt(),
                0xFFD32F2F.toInt(), 0xFF388E3C.toInt(), 0xFFF57C00.toInt()
            )
            "theme_key_bg" -> intArrayOf(
                0xFFFFFFFF.toInt(), 0xFFE8EAED.toInt(), 0xFF3C4043.toInt(),
                0xFFE3F2FD.toInt(), 0xFFFDECEA.toInt(), 0xFFF1F8E9.toInt()
            )
            else -> intArrayOf(
                0xFFB0B0B0.toInt(), 0xFF5F6368.toInt(), 0xFF1A73E8.toInt(),
                0xFFD32F2F.toInt(), 0xFF202124.toInt(), 0xFFFFFFFF.toInt()
            )
        }
        val names = preset.map(::toHex).toTypedArray()
        val builder = AlertDialog.Builder(this)
        builder.setTitle("選擇顏色")
        builder.setItems(names) { _, which ->
            if (which in preset.indices) onSelected(preset[which])
        }
        builder.show()
    }

    private fun updateThemeUi() {
        val themeMode = prefs.getString("theme_mode", ThemePalette.MODE_SYSTEM)
        customThemeContainer.visibility =
            if (themeMode == ThemePalette.MODE_CUSTOM) View.VISIBLE else View.GONE

        val custom = ThemePalette.resolve(prefs, this)
        val isCustom = themeMode == ThemePalette.MODE_CUSTOM
        val bg = if (isCustom) prefs.getInt("theme_bg", ThemePalette.light().bg) else custom.bg
        val text = if (isCustom) prefs.getInt("theme_text", ThemePalette.light().text) else custom.text
        val keyBg = if (isCustom) prefs.getInt("theme_key_bg", ThemePalette.light().keyBg) else custom.keyBg
        val border = if (isCustom) prefs.getInt("theme_border", ThemePalette.light().border) else custom.border

        applyPreview(btnThemeBg, bg)
        applyPreview(btnThemeText, text)
        applyPreview(btnThemeKeyBg, keyBg)
        applyPreview(btnThemeBorder, border)
        etThemeBg.setText(toHex(bg))
        etThemeText.setText(toHex(text))
        etThemeKeyBg.setText(toHex(keyBg))
        etThemeBorder.setText(toHex(border))
    }

    private fun refreshPreview() {
        preview.setPalette(ThemePalette.resolve(prefs, this))
    }

    private fun setupDictManagement() {
        updateDictInfo()
        btnUpdate.setOnClickListener { startDownload() }
    }

    private fun updateDictInfo() {
        val version = downloader.getLocalVersion()
        val count = downloader.getLocalEntryCount()
        tvDictVersion.text = "目前版本：$version"
        tvDictCount.text = "碼表筆數：$count"
    }

    private fun startDownload() {
        btnUpdate.isEnabled = false
        progressUpdate.visibility = ProgressBar.VISIBLE
        progressUpdate.progress = 0
        tvUpdateStatus.text = "正在下載碼表..."

        lifecycleScope.launch {
            downloader.downloadDictionary(object : DictionaryDownloader.DownloadCallback {
                override fun onProgress(progress: Int) {
                    runOnUiThread {
                        progressUpdate.progress = progress
                        tvUpdateStatus.text = when {
                            progress < 50 -> "正在下載..."
                            progress < 70 -> "正在解析碼表..."
                            progress < 100 -> "正在儲存..."
                            else -> "完成！"
                        }
                    }
                }

                override fun onSuccess(entryCount: Int, version: String) {
                    runOnUiThread {
                        progressUpdate.visibility = ProgressBar.GONE
                        btnUpdate.isEnabled = true
                        tvUpdateStatus.text = "更新完成！共 $entryCount 筆"
                        updateDictInfo()
                        Toast.makeText(
                            this@SettingsActivity,
                            "碼表更新成功！共 $entryCount 筆",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        progressUpdate.visibility = ProgressBar.GONE
                        btnUpdate.isEnabled = true
                        tvUpdateStatus.text = "更新失敗：$error"
                        Toast.makeText(
                            this@SettingsActivity,
                            "更新失敗：$error",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            })
        }
    }

    private fun setupDataButtons() {
        findViewById<Button>(R.id.btn_clear_data).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除學習資料")
                .setMessage("將清除所有自學的聯想詞與使用頻率紀錄，確定要繼續嗎？")
                .setPositiveButton("清除") { _, _ ->
                    clearLearnedData()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        findViewById<Button>(R.id.btn_export_settings).setOnClickListener {
            exportLauncher.launch("boshiamy-settings.json")
        }

        findViewById<Button>(R.id.btn_import_settings).setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    private fun setupUpdateChecker() {
        tvAppVersion = findViewById(R.id.tv_app_version)
        btnCheckUpdate = findViewById(R.id.btn_check_update)
        tvUpdateStatus = findViewById(R.id.tv_update_status2)
        appUpdater = AppUpdater(this)

        tvAppVersion.text = "目前版本：v${currentVersionName()}"
        btnCheckUpdate.setOnClickListener {
            checkForUpdate(showResult = true)
        }
        checkForUpdate(showResult = false)
    }

    private fun currentVersionName(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }

    private fun checkForUpdate(showResult: Boolean) {
        tvUpdateStatus.text = if (showResult) "正在檢查更新..." else "正在檢查更新（自動）..."
        val current = currentVersionName()
        lifecycleScope.launch {
            val result = appUpdater.checkUpdate(current)
            runOnUiThread {
                when {
                    result.error != null -> {
                        tvUpdateStatus.text = if (showResult) {
                            "檢查失敗：${result.error}"
                        } else {
                            ""
                        }
                    }
                    !result.updateAvailable -> {
                        tvUpdateStatus.text = if (result.latestVersion != null) {
                            "已是最新版本（v${result.latestVersion}）"
                        } else {
                            "已是最新版本"
                        }
                    }
                    else -> {
                        tvUpdateStatus.text = "發現新版 v${result.latestVersion}！"
                        promptDownload(result.apkUrl, result.apkName, result.latestVersion)
                    }
                }
            }
        }
    }

    private fun promptDownload(apkUrl: String?, apkName: String?, latest: String?) {
        if (apkUrl.isNullOrEmpty() || apkName.isNullOrEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("發現新版 v$latest")
            .setMessage("有新版本可以更新，是否現在下載並安裝？")
            .setPositiveButton("下載並安裝") { _, _ ->
                startDownloadUpdate(apkUrl, apkName)
            }
            .setNegativeButton("稍後", null)
            .show()
    }

    private fun startDownloadUpdate(apkUrl: String, apkName: String) {
        tvUpdateStatus.text = "正在下載新版..."
        lifecycleScope.launch {
            val file = appUpdater.downloadApk(apkUrl, apkName)
            runOnUiThread {
                if (file != null) {
                    tvUpdateStatus.text = "下載完成，準備安裝..."
                    installApk(file)
                } else {
                    tvUpdateStatus.text = "下載失敗，請稍後再試"
                }
            }
        }
    }

    @SuppressLint("QueryPermissionsNeeded")
    private fun installApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !packageManager.canRequestPackageInstalls()
        ) {
            Toast.makeText(this, "請先允許「安裝未知來源應用程式」", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
            }
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            tvUpdateStatus.text = "請於安裝畫面按下「安裝」"
        } catch (e: Exception) {
            tvUpdateStatus.text = "無法啟動安裝：${e.message}"
        }
    }

    private fun clearLearnedData() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("freq_") || key.startsWith("assoc_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        Toast.makeText(this, "學習資料已清除", Toast.LENGTH_SHORT).show()
    }

    private fun exportSettings(uri: Uri) {
        try {
            val filtered = prefs.all.filterKeys { it in allowedPrefKeys }
            val json = JSONObject(filtered as Map<String, Any?>)
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(json.toString().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "設定已匯出", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "匯出失敗：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importSettings(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return
            val json = JSONObject(text)
            val editor = prefs.edit()
            var count = 0
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                if (key !in allowedPrefKeys) continue
                val value = json.get(key)
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is Long -> editor.putInt(key, value.toInt())
                    is String -> editor.putString(key, value)
                    else -> {}
                }
                count++
            }
            editor.apply()
            reloadAllUi()
            Toast.makeText(this, "已匯入 $count 項設定", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "匯入失敗：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reloadAllUi() {
        setupInputModeSpinner()
        setupKeyboardScaleSpinner()
        setupDeleteKeySpinner()
        setupThemeModeSpinner()
        val vibrateOn = prefs.getBoolean("vibrate", true)
        findViewById<Switch>(R.id.switch_vibrate).isChecked = vibrateOn
        findViewById<View>(R.id.vibrate_strength_row).visibility =
            if (vibrateOn) View.VISIBLE else View.GONE
        val strength = prefs.getInt("vibrate_strength", 50).coerceIn(0, 100)
        findViewById<SeekBar>(R.id.seek_vibrate_strength).progress = strength
        findViewById<TextView>(R.id.tv_vibrate_strength).text = strength.toString()
        findViewById<Switch>(R.id.switch_sound).isChecked =
            prefs.getBoolean("sound", false)
        findViewById<Switch>(R.id.switch_full_width).isChecked =
            prefs.getBoolean("full_width", false)
        updateThemeUi()
        refreshPreview()
    }
}