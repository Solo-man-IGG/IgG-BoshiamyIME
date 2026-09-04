package tw.igg.boshiamyime

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import tw.igg.boshiamyime.data.DictionaryDownloader

class SettingsActivity : AppCompatActivity() {

    private lateinit var downloader: DictionaryDownloader
    private lateinit var tvDictVersion: TextView
    private lateinit var tvDictCount: TextView
    private lateinit var btnUpdate: Button
    private lateinit var progressUpdate: ProgressBar
    private lateinit var tvUpdateStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        downloader = DictionaryDownloader(this)

        tvDictVersion = findViewById(R.id.tv_dict_version)
        tvDictCount = findViewById(R.id.tv_dict_count)
        btnUpdate = findViewById(R.id.btn_update_dict)
        progressUpdate = findViewById(R.id.progress_update)
        tvUpdateStatus = findViewById(R.id.tv_update_status)

        updateDictInfo()

        btnUpdate.setOnClickListener { startDownload() }

        val rgInputMode = findViewById<RadioGroup>(R.id.rg_input_mode)
        val rgBoshiamyMode = findViewById<RadioGroup>(R.id.rg_boshiamy_mode)

        rgInputMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_t9 -> "T9"
                R.id.rb_qwerty -> "QWERTY"
                R.id.rb_zhuyin -> "ZHUYIN"
                else -> "T9"
            }
            getSharedPreferences("boshiamy_settings", MODE_PRIVATE)
                .edit().putString("input_mode", mode).apply()
            Toast.makeText(this, "已切換為 $mode 模式", Toast.LENGTH_SHORT).show()
        }

        rgBoshiamyMode.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rb_standard -> "standard"
                R.id.rb_simplified -> "simplified"
                R.id.rb_eten -> "eten"
                else -> "standard"
            }
            getSharedPreferences("boshiamy_settings", MODE_PRIVATE)
                .edit().putString("boshiamy_mode", mode).apply()
            Toast.makeText(this, "已切換為 $mode 模式", Toast.LENGTH_SHORT).show()
        }

        loadSavedSettings(rgInputMode, rgBoshiamyMode)
        setupThemeButtons()
    }

    private fun setupThemeButtons() {
        val prefs = getSharedPreferences("boshiamy_prefs", MODE_PRIVATE)
        val btnBg = findViewById<Button>(R.id.btn_theme_bg)
        val btnText = findViewById<Button>(R.id.btn_theme_text)
        val btnKeyBg = findViewById<Button>(R.id.btn_theme_keybg)
        val btnBorder = findViewById<Button>(R.id.btn_theme_border)
        val btnReset = findViewById<Button>(R.id.btn_theme_reset)

        fun applyPreview(btn: Button, color: Int) {
            btn.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(color)
                cornerRadius = 8f
            }
        }

        fun updatePreviews() {
            applyPreview(btnBg, prefs.getInt("theme_bg", 0xFFD6D6D6.toInt()))
            applyPreview(btnText, prefs.getInt("theme_text", 0xFF000000.toInt()))
            applyPreview(btnKeyBg, prefs.getInt("theme_key_bg", 0xFFFFFFFF.toInt()))
            applyPreview(btnBorder, prefs.getInt("theme_border", 0xFFB0B0B0.toInt()))
        }

        updatePreviews()

        fun pickAndSave(title: String, key: String, colors: IntArray, names: Array<String>, default: Int) {
            showColorPicker(title, colors, names) { color ->
                prefs.edit().putInt(key, color).apply()
                updatePreviews()
                Toast.makeText(this, "已套用（立即生效）", Toast.LENGTH_SHORT).show()
            }
        }

        btnBg.setOnClickListener {
            pickAndSave(
                "選擇鍵盤背景色", "theme_bg",
                intArrayOf(0xFFD6D6D6.toInt(), 0xFF2B2B2B.toInt(), 0xFF1A1A2E.toInt(),
                    0xFF0F3460.toInt(), 0xFF16213E.toInt(), 0xFF533483.toInt()),
                arrayOf("預設灰", "深黑", "深藍", "海洋藍", "暗夜", "紫色"),
                0xFFD6D6D6.toInt()
            )
        }

        btnText.setOnClickListener {
            pickAndSave(
                "選擇按鍵文字色", "theme_text",
                intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF1A73E8.toInt(),
                    0xFFD32F2F.toInt(), 0xFF388E3C.toInt(), 0xFFF57C00.toInt()),
                arrayOf("黑色", "白色", "藍色", "紅色", "綠色", "橘色"),
                0xFF000000.toInt()
            )
        }

        btnKeyBg.setOnClickListener {
            pickAndSave(
                "選擇按鍵背景色", "theme_key_bg",
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFE8EAED.toInt(), 0xFF3C4043.toInt(),
                    0xFFE3F2FD.toInt(), 0xFFFDECEA.toInt(), 0xFFF1F8E9.toInt()),
                arrayOf("白色", "淺灰", "深灰", "淺藍", "淺紅", "淺綠"),
                0xFFFFFFFF.toInt()
            )
        }

        btnBorder.setOnClickListener {
            pickAndSave(
                "選擇按鍵框線色", "theme_border",
                intArrayOf(0xFFB0B0B0.toInt(), 0xFF5F6368.toInt(), 0xFF1A73E8.toInt(),
                    0xFFD32F2F.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
                arrayOf("淺灰", "深灰", "藍色", "紅色", "黑色", "白色"),
                0xFFB0B0B0.toInt()
            )
        }

        btnReset.setOnClickListener {
            prefs.edit()
                .remove("theme_bg")
                .remove("theme_text")
                .remove("theme_key_bg")
                .remove("theme_key_pressed")
                .remove("theme_border")
                .apply()
            updatePreviews()
            Toast.makeText(this, "已恢復預設色彩（立即生效）", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showColorPicker(title: String, colors: IntArray, names: Array<String>, onSelected: (Int) -> Unit) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        val items = names.map { name -> name }.toTypedArray()
        builder.setItems(items) { _, which ->
            onSelected(colors[which])
        }
        builder.show()
    }

    private fun loadSavedSettings(rgInputMode: RadioGroup, rgBoshiamyMode: RadioGroup) {
        val prefs = getSharedPreferences("boshiamy_settings", MODE_PRIVATE)
        when (prefs.getString("input_mode", "T9")) {
            "T9" -> rgInputMode.check(R.id.rb_t9)
            "QWERTY" -> rgInputMode.check(R.id.rb_qwerty)
            "ZHUYIN" -> rgInputMode.check(R.id.rb_zhuyin)
        }
        when (prefs.getString("boshiamy_mode", "standard")) {
            "standard" -> rgBoshiamyMode.check(R.id.rb_standard)
            "simplified" -> rgBoshiamyMode.check(R.id.rb_simplified)
            "eten" -> rgBoshiamyMode.check(R.id.rb_eten)
        }
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
}
