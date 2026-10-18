package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.*

internal object MetroStyle {
    val background = 0xff080b14.toInt()
    val text = 0xfff5f7ff.toInt()
    val muted = 0xffa9b6d0.toInt()
    val mint = 0xff8af5dc.toInt()
    val blue = 0xff9cbeff.toInt()
    fun surface(start: Int, end: Int, radius: Float = 0f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)
    ).apply { cornerRadius = radius }
}

/** Static OLED-friendly aurora. Only the entrance transition animates; no perpetual redraw loop. */
internal class AuroraSurface(context: Context) : FrameLayout(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { setWillNotDraw(false); setBackgroundColor(MetroStyle.background) }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = RadialGradient(w * .88f, h * .16f, w * .95f,
            intArrayOf(0xff27376a.toInt(), 0xff10172e.toInt(), MetroStyle.background), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = RadialGradient(w * .05f, h * .55f, w * .8f,
            intArrayOf(0x1933ecc8, 0x0033ecc8), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = 0x12558ee8; paint.style = Paint.Style.STROKE; paint.strokeWidth = resources.displayMetrics.density
        canvas.drawCircle(w * 1.04f, h * .28f, w * .61f, paint)
        canvas.drawCircle(w * 1.04f, h * .28f, w * .68f, paint)
        paint.style = Paint.Style.FILL
    }
}

internal class MetroTile(context: Context, start: Int, end: Int) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        background = MetroStyle.surface(start, end, density * 3f)
        foreground = RippleDrawable(ColorStateList.valueOf(0x22ffffff), null,
            MetroStyle.surface(-1, -1, density * 3f))
        elevation = 2 * density
        clipToOutline = true
    }
}

/** Weather illustration drawn natively: no bitmap downloads and no fake weather information. */
internal class WeatherArt(context: Context, private val code: Int, private val night: Boolean) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
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
