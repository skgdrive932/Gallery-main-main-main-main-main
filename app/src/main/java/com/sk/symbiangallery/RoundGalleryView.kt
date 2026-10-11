package com.sk.symbiangallery

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Lightweight 3D carousel. It only keeps 7 cards on screen even when the gallery has
 * hundreds of photos, so dragging stays smooth and does not rebuild the whole gallery.
 */
class RoundGalleryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var items: List<MediaItem> = emptyList()
    private var current = 0
    private var position = 0f
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false
    private var onClick: ((MediaItem) -> Unit)? = null
    private val cards = mutableListOf<ImageView>()
    private val boundIndices = IntArray(7) { -1 }
    private var animator: ValueAnimator? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val cardOffsets = intArrayOf(-3, -2, -1, 0, 1, 2, 3)

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
        cameraDistance = 9000f * resources.displayMetrics.density
    }

    fun submitItems(newItems: List<MediaItem>, click: (MediaItem) -> Unit) {
        animator?.cancel()
        items = newItems
        current = 0
        position = 0f
        onClick = click
        removeAllViews()
        cards.clear()
        boundIndices.fill(-1)
        addRingAndHint()

        // Only seven real ImageViews are kept. This is the key performance fix.
        repeat(cardOffsets.size) { slot ->
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = context.getDrawable(R.drawable.bg_round_card)
                clipToOutline = true
                cameraDistance = 9000f * resources.displayMetrics.density
                isClickable = false
                isFocusable = false
            }
            cards.add(image)
            addView(image, LayoutParams(dp(172), dp(224), Gravity.CENTER))
        }
        post { updatePositions(true) }
    }

    private fun addRingAndHint() {
        val ring = TextView(context).apply {
            background = context.getDrawable(R.drawable.bg_round_ring)
            alpha = .28f
            isClickable = false
        }
        addView(ring, LayoutParams(dp(238), dp(238), Gravity.CENTER))
        val hint = TextView(context).apply {
            text = "3D Round\nSwipe smoothly"
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(185, 217, 231))
            textSize = 12f
            isClickable = false
        }
        addView(hint, LayoutParams(dp(250), dp(60), Gravity.CENTER))
    }

    private fun updatePositions(forceImages: Boolean = false) {
        if (items.isEmpty() || cards.isEmpty()) {
            cards.forEach { it.visibility = GONE }
            return
        }
        val total = items.size
        val base = floor(position).toInt()
        val fraction = position - floor(position)

        cards.forEachIndexed { slot, card ->
            val logical = wrapIndex(base + cardOffsets[slot], total)
            if (forceImages || boundIndices[slot] != logical) {
                boundIndices[slot] = logical
                Glide.with(this)
                    .load(items[logical].uri)
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(card)
            }

            val d = cardOffsets[slot] - fraction
            val angle = (d * 34f).coerceIn(-70f, 70f)
            val radians = Math.toRadians(angle.toDouble())
            val x = sin(radians).toFloat() * dp(210)
            val z = (cos(radians).toFloat() * dp(220)) - dp(220)
            val depth = 1f - (abs(d).coerceAtMost(3f) / 3f)
            val scale = 0.72f + 0.46f * depth

            card.visibility = if (abs(d) > 3.05f) INVISIBLE else VISIBLE
            card.translationX = x
            card.translationY = 0f
            card.translationZ = z
            card.rotationY = -angle
            card.scaleX = scale
            card.scaleY = scale
            card.alpha = if (abs(d) > 3f) 0f else 1f
            card.elevation = (80f * depth).coerceAtLeast(4f)
            card.setColorFilter(if (abs(d) < .05f) null else ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(.55f) }))
            card.z = 10f - abs(d)
        }
        current = wrapIndex(position.roundToInt(), total)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (items.isEmpty()) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                downX = event.x
                lastX = event.x
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                if (abs(event.x - downX) > touchSlop) dragging = true
                if (dragging && items.size > 1) {
                    position = wrapPosition(position - dx / dp(170f), items.size)
                    lastX = event.x
                    updatePositions()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    animateTo(position.roundToInt().toFloat())
                } else {
                    onClick?.invoke(items[current])
                }
                dragging = false
                return true
            }
        }
        return true
    }

    private fun animateTo(target: Float) {
        if (items.isEmpty()) return
        animator?.cancel()
        val total = items.size.toFloat()
        val start = position
        var end = target
        while (end - start > total / 2f) end -= total
        while (end - start < -total / 2f) end += total
        animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 170L
            addUpdateListener {
                position = it.animatedValue as Float
                updatePositions()
            }
            start()
        }
    }

    private fun wrapIndex(value: Int, total: Int): Int {
        if (total <= 0) return 0
        var v = value % total
        if (v < 0) v += total
        return v
    }

    private fun wrapPosition(value: Float, total: Int): Float {
        if (total <= 0) return 0f
        var v = value % total
        if (v < 0f) v += total
        return v
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float): Float = v * resources.displayMetrics.density
}
