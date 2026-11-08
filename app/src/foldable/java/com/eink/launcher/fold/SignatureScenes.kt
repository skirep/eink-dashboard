package com.eink.launcher.fold

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.eink.launcher.R
import kotlin.math.*

/** The three lights are ornamental window chrome, never fake close/minimize controls. */
internal class WindowChrome(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density
        intArrayOf(0xffff6258.toInt(), 0xffffbd2e.toInt(), 0xff28c840.toInt()).forEachIndexed { i, color ->
            paint.color = color; c.drawCircle(d * (5 + i * 14), d * 6, d * 4, paint)
        }
        paint.color = 0x224a5870; paint.strokeWidth = d
        c.drawLine(0f, height - d, width.toFloat(), height - d, paint)
    }
}

/** Pixel-stepped brass/wood frames drawn natively around the original island artwork. */
internal class PirateFrame : Drawable() {
    private val p = Paint()
    override fun draw(c: Canvas) {
        val b = bounds; val d = (b.width() / 180f).coerceIn(1f, 4f)
        p.color = 0xf20d1726.toInt(); c.drawRect(b, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = d * 3
        p.color = 0xff704526.toInt(); c.drawRect(b.left + d * 2, b.top + d * 2, b.right - d * 2, b.bottom - d * 2, p)
        p.strokeWidth = d; p.color = 0xffd1a15c.toInt()
        c.drawRect(b.left + d * 4, b.top + d * 4, b.right - d * 4, b.bottom - d * 4, p)
        p.style = Paint.Style.FILL
        for (x in floatArrayOf(b.left + d * 3, b.right - d * 5)) for (y in floatArrayOf(b.top + d * 3, b.bottom - d * 5)) {
            p.color = 0xffffd88e.toInt(); c.drawRect(x, y, x + d * 2, y + d * 2, p)
        }
    }
    override fun setAlpha(alpha: Int) { p.alpha = alpha }
    override fun setColorFilter(filter: ColorFilter?) { p.colorFilter = filter }
    @Deprecated("Drawable API") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

internal object PirateBackdrop {
    private var cached: Bitmap? = null
    fun image(context: Context): Bitmap = cached ?: BitmapFactory.decodeResource(context.resources, R.drawable.pirate_harbor).also { cached = it }
}

/** Weather-toned desktop ribbons. Animation follows the same focus/battery policy as the weather. */
internal class DesktopScene(context: Context, private val code: Int, private val night: Boolean, private val motion: Boolean) : FrameLayout(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            if (!allowed()) { running = false; invalidate(); return }
            invalidate(); postDelayed(this, 33)
        }
    }
    init { setWillNotDraw(false); setBackgroundColor(0xff172344.toInt()) }
    private fun allowed() = motion && !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode &&
        (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
    private fun sync() {
        removeCallbacks(frame)
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus() && allowed()
        if (running) post(frame)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { running = false; removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); sync() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); sync() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); if (w == 0f || h == 0f) return
        val t = if (running) (SystemClock.uptimeMillis() % 180000L) / 180000f * 6.28f else 0f
        val wet = code >= 51
        p.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(if (night) 0xff101d4c.toInt() else 0xff315a98.toInt(), if (wet) 0xff485b85.toInt() else 0xffa5579f.toInt(), 0xff101b4a.toInt()), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        for (i in 0..4) {
            val sway = sin(t + i) * w * .025f
            path.reset(); path.moveTo(-w * .4f, h * (.15f + i * .14f))
            path.cubicTo(w * (.4f + i * .07f) + sway, -h * .1f, w * .15f, h * 1.2f, w * 1.4f, h * (.4f + i * .1f))
            path.lineTo(w * 1.4f, h * 1.4f); path.lineTo(-w * .4f, h * 1.4f); path.close()
            p.shader = LinearGradient(0f, h * .2f, w, h,
                intArrayOf(intArrayOf(0xaa79c8f6.toInt(),0xcc4a77d3.toInt(),0xcc8c72dd.toInt(),0xd0de7fbe.toInt(),0xff1c3876.toInt())[i], 0x80302367.toInt()), null, Shader.TileMode.CLAMP)
            c.drawPath(path, p)
        }
        p.shader = null
    }
}

/** Illustrated map paired with real, accessible action buttons in the captain's dialog. */
internal class CaptainChart(context: Context) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        p.color = 0xffdfc48e.toInt(); c.drawRect(0f, 0f, w, h, p)
        p.color = 0x40847a54; p.strokeWidth = 1f
        for (i in 1..8) c.drawLine(w * i / 9, 0f, w * i / 9, h, p)
        for (i in 1..4) c.drawLine(0f, h * i / 5, w, h * i / 5, p)
        for (i in 0..3) {
            val x = w * (.16f + i * .22f); val y = h * (if (i % 2 == 0) .4f else .65f)
            p.color = 0xff557661.toInt(); c.drawOval(x-w*.07f, y-h*.13f, x+w*.09f, y+h*.1f, p)
            p.color = 0xffe6d39e.toInt(); c.drawOval(x-w*.045f, y-h*.1f, x+w*.06f, y+h*.06f, p)
        }
        p.style = Paint.Style.STROKE; p.color = 0xff9b3d33.toInt(); p.strokeWidth = 3f
        p.pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
        val route = Path().apply { moveTo(w*.15f,h*.4f); cubicTo(w*.4f,h*.15f,w*.45f,h*.9f,w*.82f,h*.6f) }
        c.drawPath(route,p);p.pathEffect=null;p.style=Paint.Style.FILL
        p.typeface = Typeface.create("serif",Typeface.BOLD);p.textSize=h*.16f
        c.drawText("×",w*.8f,h*.65f,p)
        p.color=0xff34333d.toInt();p.textSize=h*.12f;c.drawText("N ↑",w*.82f,h*.2f,p)
    }
}
