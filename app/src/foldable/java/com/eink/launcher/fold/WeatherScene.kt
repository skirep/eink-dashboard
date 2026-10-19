package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.widget.FrameLayout
import kotlin.math.*

/** A continuous, procedural panorama. Weather always comes from the fixed Cabrils cache. */
internal class WeatherScene(
    context: Context,
    private val code: Int,
    private val night: Boolean,
    private val motion: Boolean,
    private val unfold: Boolean = false,
    val wallpaper: Bitmap? = null,
    private val weatherOverlay: Boolean = true
) : FrameLayout(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = RectF()
    private val born = SystemClock.uptimeMillis()
    private var touchX = 0f
    private var touchY = 0f
    private var driftX = 0f
    private var driftY = 0f
    private var running = false
    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            if (!canMove()) { running = false; invalidate(); return }
            driftX += (touchX - driftX) * .065f
            driftY += (touchY - driftY) * .065f
            invalidate()
            postDelayed(this, 33L)
        }
    }
    init { setWillNotDraw(false); setBackgroundColor(MetroStyle.background) }
    private fun canMove() = motion && !(context.getSystemService(Context.POWER_SERVICE) as PowerManager).isPowerSaveMode &&
        (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
    private fun syncMotion() {
        removeCallbacks(frame)
        running = isAttachedToWindow && windowVisibility == VISIBLE && hasWindowFocus() && canMove()
        if (running) post(frame)
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); syncMotion() }
    override fun onDetachedFromWindow() { running = false; removeCallbacks(frame); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); syncMotion() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); syncMotion() }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (canMove()) {
            touchX = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) 0f else event.x / width.coerceAtLeast(1) - .5f
            touchY = if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) 0f else event.y / height.coerceAtLeast(1) - .5f
        }
        return super.dispatchTouchEvent(event)
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (width == 0 || height == 0) return
        val moving = canMove()
        val t = if (moving) (SystemClock.uptimeMillis() % 360000L) / 1000f else 0f
        val reveal = if (moving && unfold) (1f - ((SystemClock.uptimeMillis() - born) / 950f).coerceIn(0f, 1f)).pow(3) else 0f
        val w = width.toFloat(); val h = height.toFloat()
        val wet = code in 51..67 || code in 80..82 || code >= 95
        val snow = code in 71..77 || code in 85..86
        val cloudy = code >= 2
        val known = code >= 0
        val top = when { !known -> 0xff101c38; night -> 0xff091126; wet || snow -> 0xff203447; else -> 0xff123b63 }.toInt()
        val horizon = when { !known -> 0xff243652; night -> 0xff34456b; wet || snow -> 0xff648084; else -> 0xffc58a72 }.toInt()
        if (wallpaper == null) {
        p.shader = LinearGradient(0f, 0f, w * .55f, h, intArrayOf(top, horizon, 0xff102a38.toInt()), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        c.drawPaint(p); p.shader = null
        }
        c.save()
        c.translate(driftX * w * .025f, driftY * h * .015f)
        c.scale(1.04f + reveal * .14f, 1.04f + reveal * .14f, w * .58f, h * .42f)
        wallpaper?.let { image ->
            val scale = max(w / image.width, h / image.height)
            val iw = image.width * scale; val ih = image.height * scale
            imageBounds.set((w - iw) / 2f, (h - ih) / 2f, (w + iw) / 2f, (h + ih) / 2f)
            c.drawBitmap(image, null, imageBounds, imagePaint)
            if (weatherOverlay && known) {
                p.color = if (night) 0x55101a38 else 0x151a3549
                c.drawRect(0f, 0f, w, h, p)
            }
        }
        val atmosphere = wallpaper == null || weatherOverlay
        if (known && atmosphere) {
            val sx = w * .79f; val sy = h * .22f; val r = min(w, h) * .075f
            p.shader = RadialGradient(sx, sy, r * 4.5f, intArrayOf(if (night) 0x6084adff else 0x70ffd6a2, 0x00ffd6a2), null, Shader.TileMode.CLAMP)
            c.drawCircle(sx, sy, r * 4.5f, p); p.shader = null
            if (wallpaper == null && code <= 2) {
                p.color = if (night) 0xffe3edff.toInt() else 0xffffe3b2.toInt(); c.drawCircle(sx, sy, r, p)
                if (night) { p.color = top; c.drawCircle(sx + r * .40f, sy - r * .28f, r * .87f, p) }
            }
            if (night && !cloudy) for (i in 0..44) {
                p.color = Color.argb(70 + i % 100, 218, 234, 255)
                c.drawCircle(w * ((i * .618034f) % 1f), h * ((i * .1732f) % .55f), 1f + i % 3, p)
            }
        }
        // Long translucent cloud bands, each drifting at a different speed.
        if (atmosphere && (cloudy || !known)) for (i in 0..6) {
            val x = w * (((i * .31f + t * (.0009f + i * .00015f)) % 1.7f) - .35f)
            val y = h * (.12f + i * .067f)
            p.shader = RadialGradient(x, y, w * .48f, intArrayOf(if (night) 0x305f7395 else 0x509fbac4, 0x009fbac4), null, Shader.TileMode.CLAMP)
            c.save(); c.scale(1.8f, .24f, x, y); c.drawCircle(x, y, w * .48f, p); c.restore()
        }
        p.shader = null
        // Layered coastal relief, deliberately stylised rather than a claimed geographic map.
        if (wallpaper == null) for (layer in 0..3) {
            val base = h * (.51f + layer * .115f)
            path.reset(); path.moveTo(-w * .1f, h * 1.2f)
            path.lineTo(-w * .1f, base)
            for (j in 0..40) {
                val x = -w * .1f + w * 1.2f * j / 40f
                val wave = sin(j * .14 + layer * 1.7) * .055 + sin(j * .37 + layer) * .022
                path.lineTo(x, base + (wave * h).toFloat() + driftY * layer * 7f)
            }
            path.lineTo(w * 1.1f, h * 1.2f); path.close()
            p.shader = LinearGradient(0f, base - h * .08f, 0f, h, intArrayOf(
                when(layer) { 0 -> 0xff456778; 1 -> 0xff244c5b; 2 -> 0xff13323e; else -> 0xff081b29 }.toInt(), 0xff07101e.toInt()), null, Shader.TileMode.CLAMP)
            c.save(); c.translate(driftX * layer * 12f + reveal * (layer - 1) * w * .08f, 0f); c.drawPath(path, p); c.restore()
        }
        p.shader = null
        if (atmosphere && (wet || snow)) {
            p.strokeWidth = resources.displayMetrics.density * .65f
            for (i in 0 until if (snow) 48 else 70) {
                val x = w * ((i * .618034f + if(snow) sin(t * .18f + i) * .015f else -t * .02f).let { it - floor(it) })
                val y = h * ((i * .137f + t * if(snow) .018f else .21f).let { it - floor(it) })
                p.color = if (snow) 0x89e4f5ff.toInt() else 0x438fd5e9
                if (snow) c.drawCircle(x, y, 1.5f + i % 3, p) else c.drawLine(x, y, x - w * .008f, y + h * .025f, p)
            }
        }
        c.restore()
        // Text contrast and OLED-friendly edges over every condition.
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0x8c060d1b.toInt(), 0x18060d1b, 0xb3060d1b.toInt()), floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
    }
}
