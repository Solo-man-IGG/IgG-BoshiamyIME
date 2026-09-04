package tw.igg.boshiamyime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import tw.igg.boshiamyime.R

class KeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnKeyPressListener {
        fun onKeyPress(key: String)
        fun onLongPress(key: String)
    }

    private var listener: OnKeyPressListener? = null
    private var pressedKey: String? = null
    private var pressTime = 0L
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val longPressDelay = 500L

    private var keyboardBgColor = Color.parseColor("#D6D6D6")
    private var keyTextColor = Color.parseColor("#000000")
    private var keyBgColor = Color.parseColor("#FFFFFF")
    private var keyPressedBgColor = Color.parseColor("#CCCCCC")
    private var keyBorderColor = Color.parseColor("#B0B0B0")

    private val keyBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.key_background)
    }

    private val keyPressedBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.key_pressed)
    }

    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.key_border)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.key_text)
        textSize = 42f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val keySubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70757A")
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    private val keyboardBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.keyboard_background)
    }

    private var keys = listOf<List<KeyData>>()
    private val keyRects = mutableMapOf<String, RectF>()
    private val tempRect = RectF()
    private var currentLayout: KeyboardLayout = KeyboardLayout.T9
    var isShifted = false
        private set

    enum class KeyboardLayout { T9, QWERTY, ZHUYIN, NUMBER, SYMBOL, EMOJI }

    data class KeyData(
        val label: String,
        val subLabel: String = "",
        val width: Int = 1,
        val isAction: Boolean = false
    )

    fun setOnKeyPressListener(l: OnKeyPressListener?) {
        listener = l
    }

    fun setShifted(shifted: Boolean) {
        isShifted = shifted
        if (currentLayout == KeyboardLayout.QWERTY) {
            keys = createLayout(KeyboardLayout.QWERTY)
        }
        invalidate()
    }

    fun setThemeColors(
        bgColor: Int,
        textColor: Int,
        keyBg: Int,
        keyPressedBg: Int,
        borderColor: Int = keyBorderColor
    ) {
        keyboardBgColor = bgColor
        keyTextColor = textColor
        keyBgColor = keyBg
        keyPressedBgColor = keyPressedBg
        keyBorderColor = borderColor

        keyboardBackground.color = bgColor
        keyTextPaint.color = textColor
        keyBackground.color = keyBg
        keyPressedBackground.color = keyPressedBg
        keyBorderPaint.color = borderColor
        invalidate()
    }

    fun setLayout(layout: KeyboardLayout) {
        currentLayout = layout
        keys = createLayout(layout)
        keyRects.clear()
        requestLayout()
        invalidate()
    }

    private fun createLayout(layout: KeyboardLayout): List<List<KeyData>> {
        return when (layout) {
            KeyboardLayout.T9 -> listOf(
                listOf(KeyData("1", ""), KeyData("2", "ABC"), KeyData("3", "DEF")),
                listOf(KeyData("4", "GHI"), KeyData("5", "JKL"), KeyData("6", "MNO")),
                listOf(KeyData("7", "PQRS"), KeyData("8", "TUV"), KeyData("9", "WXYZ")),
                listOf(KeyData(",", ""), KeyData("0"), KeyData(".", "")),
                listOf(KeyData("space", width = 2), KeyData("⏎"))
            )
            KeyboardLayout.QWERTY -> listOf(
                listOf(
                    KeyData("1"), KeyData("2"), KeyData("3"),
                    KeyData("4"), KeyData("5"), KeyData("6"),
                    KeyData("7"), KeyData("8"), KeyData("9"),
                    KeyData("0")
                ),
                listOf(
                    KeyData("q"), KeyData("w"), KeyData("e"), KeyData("r"), KeyData("t"),
                    KeyData("y"), KeyData("u"), KeyData("i"), KeyData("o"), KeyData("p")
                ),
                listOf(
                    KeyData("a"), KeyData("s"), KeyData("d"), KeyData("f"), KeyData("g"),
                    KeyData("h"), KeyData("j"), KeyData("k"), KeyData("l")
                ),
                listOf(
                    KeyData("⇧", width = 1, isAction = true),
                    KeyData("z"), KeyData("x"), KeyData("c"),
                    KeyData("v"), KeyData("b"), KeyData("n"), KeyData("m"),
                    KeyData("⏎", width = 1, isAction = true)
                ),
                listOf(
                    KeyData(",", ""), KeyData("space", width = 7),
                    KeyData(".", "")
                )
            )
            KeyboardLayout.ZHUYIN -> listOf(
                listOf(
                    KeyData("ㄅ"), KeyData("ㄉ"), KeyData("ˇ"),
                    KeyData("ˋ"), KeyData("ㄓ"), KeyData("ˊ"),
                    KeyData("˙"), KeyData("ㄚ"), KeyData("ㄞ"),
                    KeyData("ㄢ")
                ),
                listOf(
                    KeyData("ㄆ"), KeyData("ㄊ"), KeyData("ㄍ"),
                    KeyData("ㄐ"), KeyData("ㄔ"), KeyData("ㄗ"),
                    KeyData("ㄧ"), KeyData("ㄛ"), KeyData("ㄟ"),
                    KeyData("ㄣ")
                ),
                listOf(
                    KeyData("ㄇ"), KeyData("ㄋ"), KeyData("ㄎ"),
                    KeyData("ㄑ"), KeyData("ㄕ"), KeyData("ㄘ"),
                    KeyData("ㄨ"), KeyData("ㄜ"), KeyData("ㄠ"),
                    KeyData("ㄤ")
                ),
                listOf(
                    KeyData("ㄈ"), KeyData("ㄌ"), KeyData("ㄏ"),
                    KeyData("ㄒ"), KeyData("ㄖ"), KeyData("ㄙ"),
                    KeyData("ㄩ"), KeyData("ㄝ"), KeyData("ㄡ"),
                    KeyData("ㄥ")
                ),
                listOf(
                    KeyData("ㄦ"),
                    KeyData("space", width = 4), KeyData("⏎")
                )
            )
            KeyboardLayout.NUMBER -> listOf(
                listOf(
                    KeyData("1"), KeyData("2"), KeyData("3"),
                    KeyData("4"), KeyData("5"), KeyData("6"),
                    KeyData("7"), KeyData("8"), KeyData("9"),
                    KeyData("0")
                ),
                listOf(
                    KeyData("@"), KeyData("#"), KeyData("$"),
                    KeyData("_"), KeyData("&"), KeyData("-"),
                    KeyData("+"), KeyData("("), KeyData(")"),
                    KeyData("/")
                ),
                listOf(
                    KeyData("*"), KeyData("\""), KeyData("'"),
                    KeyData(":"), KeyData(";"), KeyData("!"),
                    KeyData("?"), KeyData(","), KeyData("."),
                    KeyData("\\")
                ),
                listOf(
                    KeyData("ABC", width = 2), KeyData("%"),
                    KeyData("space", width = 4), KeyData("⏎")
                )
            )
            KeyboardLayout.SYMBOL -> listOf(
                listOf(
                    KeyData("1"), KeyData("2"), KeyData("3"),
                    KeyData("4"), KeyData("5"), KeyData("6"),
                    KeyData("7"), KeyData("8"), KeyData("9"),
                    KeyData("0")
                ),
                listOf(
                    KeyData("@"), KeyData("#"), KeyData("$"),
                    KeyData("%"), KeyData("^"), KeyData("&"),
                    KeyData("*"), KeyData("("), KeyData(")"),
                    KeyData("=")
                ),
                listOf(
                    KeyData("+"), KeyData("-"), KeyData("_"),
                    KeyData("~"), KeyData("<"), KeyData(">"),
                    KeyData("[", "〔"), KeyData("]", "〕"), KeyData("{"), KeyData("}")
                ),
                listOf(
                    KeyData("\\"), KeyData("|"), KeyData(";"),
                    KeyData(":"), KeyData("\""), KeyData("'"),
                    KeyData(","), KeyData("."), KeyData("?"),
                    KeyData("!")
                ),
                listOf(
                    KeyData("ABC", width = 2), KeyData("✕", "關閉"),
                    KeyData("space", width = 4), KeyData("⏎")
                )
            )
            KeyboardLayout.EMOJI -> listOf(
                listOf(
                    KeyData("😀"), KeyData("😂"), KeyData("😍"),
                    KeyData("🤔"), KeyData("😎"), KeyData("👍"),
                    KeyData("❤️"), KeyData("🔥"), KeyData("⭐"),
                    KeyData("🎉")
                ),
                listOf(
                    KeyData("😊"), KeyData("🤣"), KeyData("🥰"),
                    KeyData("😘"), KeyData("😜"), KeyData("🤗"),
                    KeyData("😌"), KeyData("😴"), KeyData("🤓"),
                    KeyData("😇")
                ),
                listOf(
                    KeyData("💪"), KeyData("🙌"), KeyData("👏"),
                    KeyData("🤝"), KeyData("👐"), KeyData("🤲"),
                    KeyData("🙏"), KeyData("✌️"), KeyData("🤘"),
                    KeyData("👌")
                ),
                listOf(
                    KeyData("📱"), KeyData("💻"), KeyData("⌨️"),
                    KeyData("🖥️"), KeyData("🖨️"), KeyData("🖱️"),
                    KeyData("💾"), KeyData("💿"), KeyData("📷"),
                    KeyData("🎬")
                ),
                listOf(
                    KeyData("SYM", width = 2), KeyData("✕", "關閉"),
                    KeyData("space", width = 4), KeyData("⏎")
                )
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val keyHeight = (56 * resources.displayMetrics.density).toInt()
        val totalRows = keys.size
        val height = keyHeight * totalRows + (16 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), keyboardBackground)

        if (keys.isEmpty()) return

        val density = resources.displayMetrics.density
        val padding = (4 * density).toInt()
        val keyHeight = (56 * density).toInt()

        keyRects.clear()

        for ((rowIndex, row) in keys.withIndex()) {
            val totalWidth = row.sumOf { it.width }
            val keyWidth = width.toFloat() / totalWidth

            var xPos = 0f
            for (key in row) {
                tempRect.set(
                    xPos + padding,
                    (rowIndex * keyHeight + padding).toFloat(),
                    xPos + keyWidth * key.width - padding,
                    ((rowIndex + 1) * keyHeight - padding).toFloat()
                )

                val paint = if (key.label == pressedKey) keyPressedBackground else keyBackground
                canvas.drawRoundRect(tempRect, 12f, 12f, paint)
                canvas.drawRoundRect(tempRect, 12f, 12f, keyBorderPaint)

                val displayLabel = if (currentLayout == KeyboardLayout.QWERTY &&
                    isShifted && key.label.length == 1 &&
                    key.label[0] in 'a'..'z') {
                    key.label.uppercase()
                } else {
                    key.label
                }

                val centerX = tempRect.centerX()
                val centerY = tempRect.centerY()

                if (key.subLabel.isNotEmpty()) {
                    canvas.drawText(
                        displayLabel, centerX, centerY - 10f, keyTextPaint
                    )
                    canvas.drawText(
                        key.subLabel, centerX, centerY + 34f, keySubTextPaint
                    )
                } else {
                    canvas.drawText(
                        displayLabel, centerX, centerY + 14f, keyTextPaint
                    )
                }

                keyRects[key.label] = RectF(tempRect)
                xPos += keyWidth * key.width
            }
        }
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val key = getKeyAt(event.x, event.y)
                if (key != null) {
                    pressedKey = key
                    pressTime = System.currentTimeMillis()
                    longPressTriggered = false
                    longPressHandler.postDelayed({
                        if (pressedKey == key && !longPressTriggered) {
                            longPressTriggered = true
                            pressedKey = null
                            invalidate()
                            listener?.onLongPress(key)
                        }
                    }, longPressDelay)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                val key = getKeyAt(event.x, event.y)
                longPressHandler.removeCallbacksAndMessages(null)
                pressedKey = null
                invalidate()

                if (key != null && !longPressTriggered) {
                    performClick()
                    listener?.onKeyPress(key)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacksAndMessages(null)
                pressedKey = null
                invalidate()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun getKeyAt(x: Float, y: Float): String? {
        for ((key, rect) in keyRects) {
            if (rect.contains(x, y)) {
                return key
            }
        }
        return null
    }
}
