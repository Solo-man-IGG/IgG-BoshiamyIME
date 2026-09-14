package tw.igg.boshiamyime.theme

import android.content.Context
import android.content.res.Configuration
import android.content.SharedPreferences

object ThemePalette {

    data class Palette(
        val bg: Int,
        val text: Int,
        val keyBg: Int,
        val keyPressed: Int,
        val border: Int
    )

    fun light(): Palette = Palette(
        bg = 0xFFF1F3F4.toInt(),
        text = 0xFF202124.toInt(),
        keyBg = 0xFFFFFFFF.toInt(),
        keyPressed = 0xFFE8EAED.toInt(),
        border = 0xFFB0B0B0.toInt()
    )

    fun dark(): Palette = Palette(
        bg = 0xFF202124.toInt(),
        text = 0xFFFFFFFF.toInt(),
        keyBg = 0xFF3C4043.toInt(),
        keyPressed = 0xFF5F6368.toInt(),
        border = 0xFF5F6368.toInt()
    )

    fun isNight(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun resolve(prefs: SharedPreferences, context: Context): Palette {
        val base = if (isNight(context)) dark() else light()
        return Palette(
            bg = prefs.getInt("theme_bg", base.bg),
            text = prefs.getInt("theme_text", base.text),
            keyBg = prefs.getInt("theme_key_bg", base.keyBg),
            keyPressed = prefs.getInt("theme_key_pressed", base.keyPressed),
            border = prefs.getInt("theme_border", base.border)
        )
    }
}