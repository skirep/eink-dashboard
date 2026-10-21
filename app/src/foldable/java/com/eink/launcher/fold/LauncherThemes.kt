package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import android.widget.*

internal object LauncherThemes {
    const val METRO = "metro"
    const val TERMINAL = "terminal"
    const val CYBERPUNK = "cyberpunk"
    val ids = listOf(METRO, TERMINAL, CYBERPUNK)
    fun id(value: String?) = value?.takeIf { it in ids } ?: METRO
    fun select(value: String?) {
        MetroStyle.terminal = id(value) == TERMINAL
        MetroStyle.cyberpunk = id(value) == CYBERPUNK
    }
    fun style(view: View) {
        if (!MetroStyle.terminal && !MetroStyle.cyberpunk) return
        if (view is TextView) {
            view.typeface = Typeface.MONOSPACE
            view.letterSpacing = 0f
            view.setTextColor(if (view is Button && view.isSelected) MetroStyle.blue else MetroStyle.text)
            if (view is EditText) view.setHintTextColor(MetroStyle.muted)
        }
        // Keep actual app icons and news/photos recognizable in Cyberpunk.
        if (view is ImageView && (MetroStyle.terminal || view.tag == "theme-icon"))
            view.setColorFilter(MetroStyle.mint, PorterDuff.Mode.SRC_IN)
        if (view is ViewGroup) (0 until view.childCount).forEach { style(view.getChildAt(it)) }
    }
}

/** Transparent neon city/grid overlay; the weather or chosen photo remains underneath. */
internal class CyberpunkOverlay(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        paint.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(0xcc10051f.toInt(), 0x982c0b3e.toInt(), 0xcf061d30.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
        paint.strokeWidth = resources.displayMetrics.density
        paint.color = 0x3057edff
        val horizon = h * .7f
        for (i in -6..12) canvas.drawLine(w * .5f, horizon, w * i / 6f, h, paint)
        for (fraction in floatArrayOf(.02f, .06f, .13f, .24f, .42f, .68f, 1f)) {
            val y = horizon + (h - horizon) * fraction
            canvas.drawLine(0f, y, w, y, paint)
        }
        // Fixed geometry keeps it calm and inexpensive during sensor updates.
        for (i in 0..18) {
            val x = w * i / 18f
            val top = horizon - h * (.025f + (i * 7 % 5) * .012f)
            paint.color = 0xb5090a1e.toInt()
            canvas.drawRect(x, top, x + w / 25f, horizon, paint)
            paint.color = if (i % 2 == 0) 0x8065f7ff.toInt() else 0x90ff65d5.toInt()
            canvas.drawLine(x, top, x + w / 25f, top, paint)
        }
    }
}

/** Static CRT texture: no flashing, command execution or invented system output. */
internal class TerminalScene(context: Context) : FrameLayout(context) {
    private val paint = Paint()
    init { setWillNotDraw(false); setBackgroundColor(0xff030905.toInt()) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = (resources.displayMetrics.density * 4).coerceAtLeast(3f)
        paint.color = 0x0b75f59b
        var y = 0f
        while (y < height) { canvas.drawLine(0f, y, width.toFloat(), y, paint); y += step }
        paint.shader = RadialGradient(width * .5f, height * .25f, height.toFloat().coerceAtLeast(1f),
            intArrayOf(0x102abf63, 0x00000000), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint); paint.shader = null
    }
}
