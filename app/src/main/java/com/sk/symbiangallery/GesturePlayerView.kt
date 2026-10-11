package com.sk.symbiangallery

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.media3.ui.PlayerView
import kotlin.math.abs

class GesturePlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PlayerView(context, attrs) {
    interface Listener {
        fun onSeek(deltaMs: Long)
        fun onBrightness(delta: Float)
        fun onVolume(delta: Float)
        fun onDoubleTapSeek(forward: Boolean)
        fun onLongPressStart()
        fun onLongPressEnd()
        fun onGestureFinished()
        fun onZoom(delta: Float)
    }

    var listener: Listener? = null
    var gesturesLocked = false
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var active = false
    private var horizontal = false
    private var verticalSide = 0
    private var longPressed = false
    private var pinchStartDistance = 0f
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val scaleDetector = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: android.view.ScaleGestureDetector): Boolean {
            if (gesturesLocked) return false
            pinchStartDistance = detector.currentSpan
            return true
        }
        override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
            if (gesturesLocked) return false
            val delta = (detector.currentSpan - pinchStartDistance) / width.coerceAtLeast(1).toFloat() * 0.9f
            if (kotlin.math.abs(delta) > 0.004f) {
                listener?.onZoom(delta)
                pinchStartDistance = detector.currentSpan
            }
            return true
        }
    })

    private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (gesturesLocked || width <= 0) return true
            listener?.onDoubleTapSeek(e.x >= width / 2f)
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!gesturesLocked) {
                longPressed = true
                listener?.onLongPressStart()
            }
        }
    })

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        detector.onTouchEvent(event)
        if (!gesturesLocked) when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y
                active = false; horizontal = false; verticalSide = 0; longPressed = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (!active && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    active = true
                    horizontal = abs(dx) > abs(dy)
                    verticalSide = when {
                        downX < width * .5f -> -1
                        else -> 1
                    }
                }
                if (active && !longPressed) {
                    if (horizontal) {
                        val delta = ((event.x - lastX) / width.coerceAtLeast(1) * 45_000f).toLong()
                        if (delta != 0L) listener?.onSeek(delta)
                    } else {
                        val dyStep = event.y - lastY
                        if (abs(dyStep) >= 2f) {
                            val normalized = -dyStep / height.coerceAtLeast(1)
                            if (verticalSide < 0) listener?.onBrightness(normalized * .8f)
                            else listener?.onVolume(normalized * 8f)
                        }
                    }
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (longPressed) listener?.onLongPressEnd()
                if (active) listener?.onGestureFinished()
                active = false; longPressed = false
            }
        }
        return super.dispatchTouchEvent(event)
    }
}
