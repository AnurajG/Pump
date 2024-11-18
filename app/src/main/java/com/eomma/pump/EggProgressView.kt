package com.eomma.pump

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat

class EggProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val clipPath = Path()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    }

    init {
        setImageResource(R.drawable.ic_egg_outline)
        setWillNotDraw(false)
    }

    private fun getProgressColor(progress: Float): Int {
        return when {
            progress < 0.3f -> ContextCompat.getColor(context, R.color.barbie_pink_light)
            progress < 0.7f -> ContextCompat.getColor(context, R.color.barbie_pink)
            else -> ContextCompat.getColor(context, R.color.barbie_pink_dark)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (progress <= 0) {
            super.onDraw(canvas)
            return
        }

        // Create a layer to apply masking
        val sc = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        // Draw the egg outline
        super.onDraw(canvas)

        // Calculate fill height
        val fillHeight = height * progress
        val fillTop = height - fillHeight

        // Draw the fill rectangle with masking
        maskPaint.color = getProgressColor(progress)
        canvas.drawRect(0f, fillTop, width.toFloat(), height.toFloat(), maskPaint)

        // Restore the canvas
        canvas.restoreToCount(sc)
    }

    fun setProgress(newProgress: Float, animate: Boolean = true) {
        if (animate) {
            ValueAnimator.ofFloat(progress, newProgress).apply {
                duration = 300
                interpolator = LinearInterpolator()
                addUpdateListener {
                    progress = it.animatedValue as Float
                }
                start()
            }
        } else {
            progress = newProgress
        }
    }
}