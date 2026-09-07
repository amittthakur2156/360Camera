package com.example.threesixtycamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.abs

class Panorama360View @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var panorama: Bitmap? = null

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    )

    private var offsetX = 0f
    private var offsetY = 0f
    private var zoom = 1f

    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(
                detector: ScaleGestureDetector
            ): Boolean {
                zoom = (zoom * detector.scaleFactor)
                    .coerceIn(1f, 4f)
                invalidate()
                return true
            }
        }
    )

    fun setPanorama(bitmap: Bitmap?) {
        panorama = bitmap
        offsetX = 0f
        offsetY = 0f
        zoom = 1f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(android.graphics.Color.BLACK)

        val bitmap = panorama ?: return
        if (width <= 0 || height <= 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()

        /*
         * Fit the 2:1 panorama to the available screen.
         * It is repeated horizontally so the 0°/360° edge is seamless
         * while the user drags.
         */
        val imageRatio =
            bitmap.width.toFloat() / bitmap.height.toFloat()
        val viewRatio = viewW / viewH

        var drawW: Float
        var drawH: Float

        if (imageRatio > viewRatio) {
            drawW = viewW
            drawH = drawW / imageRatio
        } else {
            drawH = viewH
            drawW = drawH * imageRatio
        }

        drawW *= zoom
        drawH *= zoom

        val top =
            viewH / 2f - drawH / 2f + offsetY

        val baseLeft =
            viewW / 2f - drawW / 2f + offsetX

        /*
         * Draw enough copies to cover the whole screen in either
         * direction. This gives true horizontal wrap-around.
         */
        var left = baseLeft - drawW * 2f
        while (left < viewW + drawW) {
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + drawW,
                    top + drawH
                ),
                paint
            )
            left += drawW
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    offsetX += event.x - lastX
                    offsetY += event.y - lastY

                    val verticalLimit =
                        height.toFloat() * 0.25f

                    offsetY = offsetY.coerceIn(
                        -verticalLimit,
                        verticalLimit
                    )

                    /*
                     * Keep the horizontal offset bounded. The repeated
                     * drawing above makes the wrap visually continuous.
                     */
                    val limit =
                        maxOf(width.toFloat(), 1f) * 2f

                    if (abs(offsetX) > limit) {
                        offsetX =
                            if (offsetX > 0f) -limit else limit
                    }

                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }
}
