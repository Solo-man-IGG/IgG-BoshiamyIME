package tw.igg.boshiamyime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import tw.igg.boshiamyime.theme.ThemePalette

class KeyboardPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var palette = ThemePalette.light()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f
        textAlign = Paint.Align.CENTER
    }
    private val tmpRect = RectF()

    fun setPalette(p: ThemePalette.Palette) {
        palette = p
        bgPaint.color = p.bg
        keyPaint.color = p.keyBg
        pressedPaint.color = p.keyPressed
        borderPaint.color = p.border
        textPaint.color = p.text
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(palette.bg)

        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("⇧", "z", "x", "c", "v", "b", "n", "m", "⌫"),
            listOf("mode", "#+=", ",", "space", ".", "😊", "⏎")
        )
        val pressedKey = "g"

        val margin = (6 * resources.displayMetrics.density).toInt()
        val rowGap = (4 * resources.displayMetrics.density).toInt()
        val keyHeight = (height - rowGap * (rows.size - 1) - margin * 2) / rows.size

        for ((rowIndex, row) in rows.withIndex()) {
            val widths = row.map {
                when (it) {
                    "space" -> 6
                    "mode", "#+=", "😊" -> 1
                    else -> 1
                }
            }
            val total = widths.sum().coerceAtLeast(1)
            val keyWidth = (width - margin * 2) / total.toFloat()

            var xPos = margin.toFloat()
            val top = (margin + rowIndex * (keyHeight + rowGap)).toFloat()
            for ((i, label) in row.withIndex()) {
                val w = keyWidth * widths[i]
                tmpRect.set(xPos, top, xPos + w, top + keyHeight)

                val paint = if (label == pressedKey) pressedPaint else keyPaint
                canvas.drawRoundRect(tmpRect, 8f, 8f, paint)
                canvas.drawRoundRect(tmpRect, 8f, 8f, borderPaint)

                val display = when (label) {
                    "mode" -> "蝦"
                    "#+=" -> "#+="
                    "space" -> "空白＋注音"
                    else -> label
                }
                canvas.drawText(
                    display,
                    tmpRect.centerX(),
                    tmpRect.centerY() + textPaint.textSize / 3,
                    textPaint
                )
                xPos += w
            }
        }
    }
}