package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.widget.FrameLayout

/** Quiet native wallpaper; no fake network activity or shell processes. */
internal class LinuxScene(context: Context) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setWillNotDraw(false); setBackgroundColor(0xff080f17.toInt()) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        paint.shader = RadialGradient(w * .78f, h * .4f, h,
            intArrayOf(0xff173d37.toInt(), 0xff080f17.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        val step = 32f * resources.displayMetrics.density
        paint.color = 0x143bba88; paint.strokeWidth = resources.displayMetrics.density
        var x = 0f
        while (x < w) { canvas.drawLine(x, 0f, x, h, paint); x += step }
        var y = 0f
        while (y < h) { canvas.drawLine(0f, y, w, y, paint); y += step }
        paint.color = 0x1878f7b4; paint.typeface = Typeface.MONOSPACE
        paint.textSize = w * .2f
        canvas.drawText("</>", w * .52f, h * .75f, paint)
        paint.color = 0x4978f7b4; paint.textSize = 11f * resources.displayMetrics.density
        canvas.drawText("MAGICFOLD / LINUX DESKTOP", step, h - step, paint)
    }
}
