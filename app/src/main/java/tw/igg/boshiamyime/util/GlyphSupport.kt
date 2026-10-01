package tw.igg.boshiamyime.util

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import java.util.concurrent.ConcurrentHashMap

object GlyphSupport {

    private const val CACHE_LIMIT = 4096

    private val cache = ConcurrentHashMap<String, Boolean>()

    private val basePaint: Paint by lazy {
        Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = 48f
        }
    }

    private val bmpPaint: Paint? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Paint(basePaint)
        } else {
            null
        }
    }

    fun canDisplay(char: String): Boolean {
        if (char.isEmpty()) return false
        cache[char]?.let { return it }
        val result = compute(char)
        if (cache.size < CACHE_LIMIT) {
            cache[char] = result
        }
        return result
    }

    fun canDisplayAll(text: String): Boolean {
        return text.all { canDisplay(it.toString()) }
    }

    private fun compute(char: String): Boolean {
        val paint = bmpPaint ?: return true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                paint.hasGlyph(char)
            } else {
                true
            }
        } catch (e: Exception) {
            true
        }
    }
}
