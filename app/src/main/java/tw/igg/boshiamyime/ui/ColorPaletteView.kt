package tw.igg.boshiamyime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class ColorPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onColorSelected: ((Int) -> Unit)? = null

    var selectedColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    private val cols = 12
    private val colorRows = 8
    private val grayRow = 8

    private val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.argb(0x22, 0, 0, 0)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val totalRows = colorRows + 1
        setMeasuredDimension(w, (w * totalRows / cols.toFloat()).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val totalRows = colorRows + 1
        val cellW = width / cols.toFloat()
        val cellH = height / totalRows.toFloat()

        for (r in 0 until totalRows) {
            for (c in 0 until cols) {
                swatchPaint.color = colorAtCell(c, r)
                val left = c * cellW
                val top = r * cellH
                canvas.drawRect(left, top, left + cellW, top + cellH, swatchPaint)
            }
        }

        for (i in 0..totalRows) {
            val y = i * cellH
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }
        for (i in 0..cols) {
            val x = i * cellW
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
        }

        val hsv = FloatArray(3)
        Color.colorToHSV(selectedColor, hsv)
        val selCol: Int
        val selRow: Int
        if (hsv[1] < 0.06f) {
            selRow = grayRow
            selCol = ((1f - hsv[2]).coerceIn(0f, 0.999f) * cols).toInt()
        } else {
            selCol = ((hsv[0] / 360f).coerceIn(0f, 0.999f) * cols).toInt()
            selRow = ((1f - hsv[2]) / 0.9f * colorRows).toInt().coerceIn(0, colorRows - 1)
        }
        val luminance = (Color.red(selectedColor) + Color.green(selectedColor) + Color.blue(selectedColor)) / 3
        ringPaint.color = if (luminance > 128) Color.BLACK else Color.WHITE
        canvas.drawRect(
            selCol * cellW + 2f, selRow * cellH + 2f,
            (selCol + 1) * cellW - 2f, (selRow + 1) * cellH - 2f,
            ringPaint
        )
    }

    private fun colorAtCell(c: Int, r: Int): Int = when {
        r < colorRows -> {
            val hue = c / cols.toFloat() * 360f
            val value = (1f - r * (0.9f / colorRows)).coerceAtLeast(0.1f)
            Color.HSVToColor(floatArrayOf(hue, 1f, value))
        }
        else -> {
            val v = (1f - c / cols.toFloat()).coerceIn(0f, 1f)
            Color.HSVToColor(floatArrayOf(0f, 0f, v))
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val c = (event.x / (width / cols.toFloat())).toInt().coerceIn(0, cols - 1)
            val totalRows = colorRows + 1
            val r = (event.y / (height / totalRows.toFloat())).toInt().coerceIn(0, totalRows - 1)
            selectedColor = colorAtCell(c, r)
            onColorSelected?.invoke(selectedColor)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}