package com.yamanobora.offlinerecorder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val amplitudes = ArrayList<Float>()

    private val maxPoints = 120

    private val paint = Paint().apply {
        color = 0xff2196F3.toInt()
        strokeWidth = 6f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val pathTop = Path()
    private val pathBottom = Path()

    private var lastAmp = 0f

    fun addAmplitude(amp: Int) {

        // 正規化
        var normalized = amp / 15000f

        if (normalized > 1f) normalized = 1f
        if (normalized < 0f) normalized = 0f

        // ★ スムージング
        val smooth = lastAmp * 0.7f + normalized * 0.3f
        lastAmp = smooth

        amplitudes.add(smooth)

        if (amplitudes.size > maxPoints) {
            amplitudes.removeAt(0)
        }

        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (amplitudes.isEmpty()) return

        val centerY = height / 2f
        val stepX = width.toFloat() / maxPoints

        pathTop.reset()
        pathBottom.reset()

        for (i in amplitudes.indices) {

            val amp = amplitudes[i]

            // ★ 右から左に流す
            val x = width - (amplitudes.size - i) * stepX

            val yTop = centerY - amp * centerY
            val yBottom = centerY + amp * centerY

            if (i == 0) {
                pathTop.moveTo(x, yTop)
                pathBottom.moveTo(x, yBottom)
            } else {
                pathTop.lineTo(x, yTop)
                pathBottom.lineTo(x, yBottom)
            }
        }

        canvas.drawPath(pathTop, paint)
        canvas.drawPath(pathBottom, paint)
    }
}