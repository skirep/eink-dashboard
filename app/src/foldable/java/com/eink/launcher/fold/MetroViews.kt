package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.*

internal object MetroStyle {
    var terminal = false
    var cyberpunk = false
    val background get() = if (terminal) 0xff030905.toInt() else if (cyberpunk) 0xff090616.toInt() else 0xff080b14.toInt()
    val text get() = if (terminal) 0xffb7ffc4.toInt() else 0xfff5f7ff.toInt()
    val muted get() = if (terminal) 0xff80b88c.toInt() else if (cyberpunk) 0xffb8b2d6.toInt() else 0xffa9b6d0.toInt()
    val mint get() = if (terminal) 0xff75f59b.toInt() else if (cyberpunk) 0xff65f7ff.toInt() else 0xff8af5dc.toInt()
    val blue get() = if (terminal) 0xffefc778.toInt() else if (cyberpunk) 0xffff9ce3.toInt() else 0xff9cbeff.toInt()
    fun surface(start: Int, end: Int, radius: Float = 0f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        if (terminal && start != -1) intArrayOf(0xf20a170e.toInt(), 0xf2050e09.toInt())
        else if (cyberpunk && start != -1) intArrayOf(0xf227143f.toInt(), 0xf00b1830.toInt()) else intArrayOf(start, end)
    ).apply { cornerRadius = if (terminal) 0f else radius
        if (terminal && start != -1) setStroke(1, 0xff3b8052.toInt())
        else if (cyberpunk && start != -1) setStroke(2, 0xff6669b8.toInt()) }
}

internal class MetroTile(context: Context, start: Int, end: Int) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        background = MetroStyle.surface(
            (start and 0x00ffffff) or 0x99000000.toInt(),
            (end and 0x00ffffff) or 0xd9000000.toInt(), density * 7f
        ).apply { setStroke((density * .7f).roundToInt().coerceAtLeast(1), if (MetroStyle.terminal || MetroStyle.cyberpunk) MetroStyle.mint else 0x3cdbedff) }
        foreground = RippleDrawable(ColorStateList.valueOf(0x22ffffff), null,
            MetroStyle.surface(-1, -1, density * 7f))
        elevation = 10 * density
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, if (MetroStyle.terminal) 0f else density * 7f)
                outline.alpha = 1f
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            outlineAmbientShadowColor = 0xff000000.toInt()
            outlineSpotShadowColor = 0xff000000.toInt()
        }
        clipToOutline = true
    }
}

/** Weather illustration drawn natively: no bitmap downloads and no fake weather information. */
internal class WeatherArt(context: Context, private val code: Int, private val night: Boolean) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        if (MetroStyle.terminal) {
            p.color = MetroStyle.mint; p.typeface = Typeface.MONOSPACE
            p.textSize = resources.displayMetrics.density * 12; p.textAlign = Paint.Align.CENTER
            val label = when { code < 0 -> "[ -- ]"; code >= 95 -> "[ /!/ ]"; code in 71..77 || code in 85..86 -> "[ * * ]"; code >= 51 -> "[ /// ]"; code >= 2 -> "[ ~~~ ]"; night -> "[ ( ) ]"; else -> "[ (*) ]" }
            canvas.drawText(label, width / 2f, height / 2f + p.textSize / 3, p)
            return
        }
        val s = min(width, height).toFloat()
        canvas.save(); canvas.translate((width - s) / 2f, (height - s) / 2f); canvas.scale(s / 100f, s / 100f)
        if (code < 0) {
            p.color = 0xffa9c0e9.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = 2f
            canvas.drawCircle(52f, 48f, 19f, p); canvas.drawLine(52f, 38f, 52f, 58f, p); canvas.drawLine(42f, 48f, 62f, 48f, p)
            p.style = Paint.Style.FILL; canvas.restore(); return
        }
        if (code <= 2) {
            p.shader = RadialGradient(66f, 35f, 32f, intArrayOf(if(night) 0x5595b9ff else 0x55ffe0a0, 0x00ffe0a0), null, Shader.TileMode.CLAMP)
            canvas.drawCircle(66f, 35f, 32f, p); p.shader = null
            p.color = if (night) 0xffd3ddff.toInt() else 0xffffdc91.toInt(); canvas.drawCircle(66f, 35f, 17f, p)
            if (night) { p.color = 0xff26375e.toInt(); canvas.drawCircle(73f, 28f, 15f, p) }
        }
        if (code != 0) {
            p.shader = LinearGradient(0f, 35f, 0f, 74f, 0xfff5faff.toInt(), 0xffa5bfdd.toInt(), Shader.TileMode.CLAMP)
            canvas.drawCircle(34f, 54f, 16f, p); canvas.drawCircle(51f, 45f, 22f, p); canvas.drawCircle(72f, 57f, 15f, p)
            canvas.drawRoundRect(20f, 53f, 87f, 72f, 10f, 10f, p); p.shader = null
        }
        if (code >= 51) {
            p.color = 0xff84dcff.toInt(); p.strokeWidth = 3f; p.strokeCap = Paint.Cap.ROUND
            for (x in floatArrayOf(35f, 52f, 69f)) {
                if (code in 71..77 || code == 85 || code == 86) {
                    canvas.drawLine(x-3f, 84f, x+3f, 84f, p); canvas.drawLine(x, 81f, x, 87f, p)
                } else canvas.drawLine(x, 80f, x-3f, 88f, p)
            }
        }
        if (code >= 95) {
            p.color = 0xffffdc91.toInt(); val bolt = Path().apply { moveTo(57f, 67f); lineTo(45f, 83f); lineTo(54f, 83f); lineTo(48f, 97f); lineTo(67f, 77f); lineTo(57f, 77f); close() }; canvas.drawPath(bolt, p)
        }
        canvas.restore()
    }
}
