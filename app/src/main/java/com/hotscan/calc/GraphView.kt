package com.hotscan.calc

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Minimal line graph of a numeric series (used for dBm over time). */
class GraphView(context: Context) : View(context) {

    private var values: List<Int> = emptyList()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF4FC3F7"); strokeWidth = 3f; style = Paint.Style.STROKE
    }
    private val axis = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF455A64"); strokeWidth = 1.5f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFB0BEC5"); textSize = 28f
    }

    fun setValues(v: List<Int>) { values = v; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#FF0A0D10"))
        if (values.size < 2) {
            canvas.drawText("Not enough data to graph.", 30f, height / 2f, text)
            return
        }
        val padL = 90f; val padR = 20f; val padT = 20f; val padB = 40f
        val w = width - padL - padR
        val h = height - padT - padB
        val minV = values.min().toFloat()
        val maxV = values.max().toFloat()
        val range = (maxV - minV).coerceAtLeast(1f)

        canvas.drawLine(padL, padT, padL, padT + h, axis)
        canvas.drawLine(padL, padT + h, padL + w, padT + h, axis)
        canvas.drawText("${maxV.toInt()} dBm", 4f, padT + 20f, text)
        canvas.drawText("${minV.toInt()}", 4f, padT + h, text)

        val stepX = w / (values.size - 1)
        var prevX = padL
        var prevY = padT + h - (values[0] - minV) / range * h
        for (i in 1 until values.size) {
            val x = padL + stepX * i
            val y = padT + h - (values[i] - minV) / range * h
            canvas.drawLine(prevX, prevY, x, y, line)
            prevX = x; prevY = y
        }
    }
}
