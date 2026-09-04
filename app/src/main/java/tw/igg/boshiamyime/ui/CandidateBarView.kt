package tw.igg.boshiamyime.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import tw.igg.boshiamyime.R
import tw.igg.boshiamyime.model.Candidate

class CandidateBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    interface OnCandidateClickListener {
        fun onCandidateClick(candidate: Candidate, position: Int)
    }

    private var candidates = listOf<Candidate>()
    private var listener: OnCandidateClickListener? = null
    private var selectedIndex = 0
    private var scrollX = 0f
    private var lastTouchX = 0f
    private var isDragging = false
    private var isBopomofoMode = false
    private var errorText: String? = null
    private var savedCandidates = listOf<Candidate>()
    private val errorHideHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val errorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.candidate_text)
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val bopomofoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A73E8")
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.candidate_selected)
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val selectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary_container)
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 2f
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private val itemPadding = (8 * resources.displayMetrics.density).toInt()
    private var itemWidths = mutableListOf<Float>()
    private var totalContentWidth = 0f

    fun setOnCandidateClickListener(l: OnCandidateClickListener?) {
        listener = l
    }

    fun setPanelBackgroundColor(color: Int) {
        backgroundPaint.color = color
        invalidate()
    }

    fun setCandidates(newCandidates: List<Candidate>) {
        errorHideHandler.removeCallbacksAndMessages(null)
        errorText = null
        candidates = newCandidates
        isBopomofoMode = false
        selectedIndex = 0
        scrollX = 0f
        calculateItemWidths()
        invalidate()
    }

    fun showError(text: String) {
        savedCandidates = candidates
        errorText = text
        candidates = listOf(Candidate(code = "", char = text))
        isBopomofoMode = true
        selectedIndex = 0
        scrollX = 0f
        calculateItemWidths()
        invalidate()
        errorHideHandler.removeCallbacksAndMessages(null)
        errorHideHandler.postDelayed({
            if (errorText != null) {
                errorText = null
                candidates = savedCandidates
                isBopomofoMode = false
                selectedIndex = 0
                scrollX = 0f
                calculateItemWidths()
                invalidate()
            }
        }, 1800L)
    }

    fun showBopomofo(bopomofo: String) {
        if (bopomofo.isEmpty()) return
        candidates = listOf(Candidate(code = "", char = bopomofo))
        isBopomofoMode = true
        selectedIndex = 0
        scrollX = 0f
        calculateItemWidths()
        invalidate()
    }

    private fun calculateItemWidths() {
        itemWidths.clear()
        totalContentWidth = 0f
        for (candidate in candidates) {
            val paint = if (isBopomofoMode) bopomofoPaint else textPaint
            val width = paint.measureText(candidate.char) + itemPadding * 2
            itemWidths.add(width)
            totalContentWidth += width
        }
    }

    fun selectNext() {
        if (candidates.isNotEmpty()) {
            selectedIndex = (selectedIndex + 1) % candidates.size
            ensureVisible()
            invalidate()
        }
    }

    fun selectPrevious() {
        if (candidates.isNotEmpty()) {
            selectedIndex = if (selectedIndex > 0) selectedIndex - 1 else candidates.size - 1
            ensureVisible()
            invalidate()
        }
    }

    private fun ensureVisible() {
        if (selectedIndex !in itemWidths.indices) return
        var x = 0f
        for (i in 0 until selectedIndex) {
            x += itemWidths[i]
        }
        val itemWidth = itemWidths[selectedIndex]
        val viewWidth = width.toFloat()

        if (x - scrollX < 0) {
            scrollX = x
        } else if (x + itemWidth - scrollX > viewWidth) {
            scrollX = x + itemWidth - viewWidth
        }
        scrollX = scrollX.coerceIn(0f, (totalContentWidth - viewWidth).coerceAtLeast(0f))
    }

    fun getSelectedCandidate(): Candidate? {
        return if (candidates.isNotEmpty() && selectedIndex in candidates.indices) {
            candidates[selectedIndex]
        } else {
            null
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        if (candidates.isEmpty()) return

        var xPos = -scrollX
        for (i in candidates.indices) {
            val itemWidth = itemWidths[i]
            val itemCenterX = xPos + itemWidth / 2

            if (itemCenterX + itemWidth / 2 < 0 || itemCenterX - itemWidth / 2 > width) {
                xPos += itemWidth
                continue
            }

            if (i == selectedIndex && !isBopomofoMode) {
                val bgRect = android.graphics.RectF(
                    xPos, 0f, xPos + itemWidth, height.toFloat()
                )
                canvas.drawRoundRect(bgRect, 8f, 8f, selectedBgPaint)
            }

            val paint = when {
                errorText != null -> errorPaint
                isBopomofoMode -> bopomofoPaint
                i == selectedIndex -> selectedPaint
                else -> textPaint
            }
            val y = if (errorText != null || isBopomofoMode) {
                height / 2f + bopomofoPaint.textSize / 3
            } else {
                height / 2f + textPaint.textSize / 3
            }
            canvas.drawText(candidates[i].char, itemCenterX, y, paint)

            if (!isBopomofoMode && i < candidates.size - 1) {
                canvas.drawLine(
                    xPos + itemWidth, 0f,
                    xPos + itemWidth, height.toFloat(),
                    dividerPaint
                )
            }

            xPos += itemWidth
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isBopomofoMode) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                if (Math.abs(dx) > 5) {
                    isDragging = true
                }
                if (isDragging) {
                    scrollX -= dx
                    scrollX = scrollX.coerceIn(
                        0f,
                        (totalContentWidth - width).coerceAtLeast(0f)
                    )
                    lastTouchX = event.x
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDragging) {
                    val tapX = event.x + scrollX
                    var x = 0f
                    for (i in candidates.indices) {
                        val itemWidth = itemWidths[i]
                        if (tapX >= x && tapX < x + itemWidth) {
                            selectedIndex = i
                            invalidate()
                            listener?.onCandidateClick(candidates[i], i)
                            return true
                        }
                        x += itemWidth
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (48 * resources.displayMetrics.density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }
}
