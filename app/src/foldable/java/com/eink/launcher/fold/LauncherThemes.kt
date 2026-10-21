package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.view.View
import android.view.ViewGroup
import android.widget.*

internal object LauncherThemes {
    const val METRO = "metro"
    const val TERMINAL = "terminal"
    fun id(value: String?) = if (value == TERMINAL) TERMINAL else METRO
    fun style(view: View) {
        if (!MetroStyle.terminal) return
        if (view is TextView) {
            view.typeface = Typeface.MONOSPACE
            view.letterSpacing = 0f
            view.setTextColor(if (view is Button && view.isSelected) 0xffefc778.toInt() else MetroStyle.text)
            if (view is EditText) view.setHintTextColor(MetroStyle.muted)
        }
        if (view is ImageView) view.setColorFilter(MetroStyle.mint, PorterDuff.Mode.SRC_IN)
        if (view is ViewGroup) (0 until view.childCount).forEach { style(view.getChildAt(it)) }
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
