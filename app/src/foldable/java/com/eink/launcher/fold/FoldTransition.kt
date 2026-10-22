package com.eink.launcher.fold

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.max
import kotlin.math.roundToInt

/** A transition inside the launcher; the physical display hand-off belongs to MagicOS. */
internal class FoldTransition(private val completed: () -> Unit) {
    var running = false; private set
    var lastExpanded: Boolean? = null; private set
    var starts = 0; private set
    var progress = 1f; private set
    private var animator: ValueAnimator? = null
    private var content: View? = null
    private var overlay: ImageView? = null
    private var surface: FrameLayout? = null
    private var pending: ViewTreeObserver.OnPreDrawListener? = null

    fun start(surface: FrameLayout, content: View, previous: Bitmap?, expanded: Boolean) {
        finish(false)
        this.surface = surface; this.content = content
        running = true; lastExpanded = expanded; starts++
        previous?.let { image ->
            overlay = ImageView(surface.context).apply {
                tag = "fold-transition-overlay"
                setImageBitmap(image); scaleType = ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                isClickable = false; isFocusable = false
            }.also { surface.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        }
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (!surface.hasWindowFocus()) return true
                surface.viewTreeObserver.removeOnPreDrawListener(this); pending = null
                if (!running) return true
                content.pivotX = content.width / 2f; content.pivotY = content.height / 2f
                overlay?.apply { pivotX = width / 2f; pivotY = height / 2f }
                frame(0f, expanded)
                val animation = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 560L
                    interpolator = DecelerateInterpolator(1.5f)
                    addUpdateListener { frame(it.animatedValue as Float, expanded) }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) { finish() }
                    })
                }
                animator = animation
                animation.start()
                return true
            }
        }
        pending = listener
        surface.viewTreeObserver.addOnPreDrawListener(listener)
        surface.invalidate()
    }

    private fun frame(progress: Float, expanded: Boolean) {
        val root = content ?: return
        this.progress = progress
        val scale = if (expanded) .96f + .04f * progress else 1.04f - .04f * progress
        root.scaleX = scale; root.scaleY = scale
        root.alpha = .25f + .75f * progress
        blur(root, (1f - progress) * 18f)
        overlay?.apply {
            alpha = (1f - progress * 1.6f).coerceAtLeast(0f)
            scaleX = if (expanded) 1f + .06f * progress else 1f - .06f * progress
            scaleY = scaleX
            blur(this, progress * 24f)
        }
    }

    fun focusChanged(focused: Boolean) {
        if (focused) surface?.invalidate()
        else if (pending == null) finish(false)
    }

    /** A touch, focus loss or another fold always restores a fully usable, crisp hierarchy. */
    fun finish(notify: Boolean = true) {
        val wasRunning = running
        running = false; progress = 1f
        animator?.let { it.removeAllListeners(); it.removeAllUpdateListeners(); it.cancel() }
        animator = null
        pending?.let { if (surface?.viewTreeObserver?.isAlive == true) surface?.viewTreeObserver?.removeOnPreDrawListener(it) }
        pending = null
        content?.apply { alpha = 1f; scaleX = 1f; scaleY = 1f; blur(this, 0f) }
        overlay?.let { image ->
            blur(image, 0f); surface?.removeView(image); image.setImageDrawable(null)
        }
        overlay = null; content = null; surface = null
        if (wasRunning && notify) completed()
    }

    companion object {
        private fun blur(view: View, radiusDp: Float) {
            if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(if (radiusDp < .2f) null else
                RenderEffect.createBlurEffect(radiusDp * view.resources.displayMetrics.density,
                    radiusDp * view.resources.displayMetrics.density, Shader.TileMode.CLAMP))
        }
        fun capture(view: View?): Bitmap? {
            if (view == null || view.width <= 0 || view.height <= 0) return null
            return try {
                val scale = minOf(1f, 960f / max(view.width, view.height))
                Bitmap.createBitmap(max(1, (view.width * scale).roundToInt()), max(1, (view.height * scale).roundToInt()), Bitmap.Config.ARGB_8888).also {
                    val canvas = Canvas(it); canvas.scale(scale, scale); view.draw(canvas)
                }
            } catch (_: RuntimeException) { null }
            catch (_: OutOfMemoryError) { null }
        }
    }
}
