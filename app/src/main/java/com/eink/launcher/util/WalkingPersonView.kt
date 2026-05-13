package com.eink.launcher.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * WalkingPersonView – custom view that draws and animates an anime-style walking character.
 * 
 * The animation cycles through different walking poses and moves the character
 * across the screen. When tapped, the character falls down and gets back up.
 * Updates are throttled to be e-ink friendly.
 */
class WalkingPersonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class AnimationState {
        WALKING, FALLING, FALLEN, GETTING_UP
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFE4C4.toInt() // Skin tone
        style = Paint.Style.FILL
    }

    private val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A3728.toInt() // Dark brown hair
        style = Paint.Style.FILL
    }

    private val clothesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3366CC.toInt() // Blue clothes
        style = Paint.Style.FILL
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.FILL
    }

    private val blushPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF9999.toInt() // Pink blush
        style = Paint.Style.FILL
        alpha = 100
    }

    private var positionX = 200f
    private var positionY = 0f // Will be set in onDraw
    private var walkCycle = 0
    private var animationState = AnimationState.WALKING
    private var fallProgress = 0f
    private var stateTimer = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    // Scale factor for larger character
    private val scale = 2.5f

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                when (animationState) {
                    AnimationState.WALKING -> {
                        positionX += 20f
                        walkCycle = (walkCycle + 1) % 4
                        
                        if (positionX > width + 150 * scale) {
                            positionX = -150f * scale
                        }
                    }
                    AnimationState.FALLING -> {
                        fallProgress += 0.15f
                        if (fallProgress >= 1f) {
                            fallProgress = 1f
                            animationState = AnimationState.FALLEN
                            stateTimer = 0
                        }
                    }
                    AnimationState.FALLEN -> {
                        stateTimer++
                        if (stateTimer > 3) { // Wait 1.5 seconds
                            animationState = AnimationState.GETTING_UP
                            stateTimer = 0
                        }
                    }
                    AnimationState.GETTING_UP -> {
                        fallProgress -= 0.15f
                        if (fallProgress <= 0f) {
                            fallProgress = 0f
                            animationState = AnimationState.WALKING
                        }
                    }
                }
                
                invalidate()
                handler.postDelayed(this, 500) // Update every 500ms for e-ink
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAnimation()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val touchX = event.x
            val touchY = event.y
            
            // Check if touch is near the character
            val charY = height * 0.65f
            val distance = Math.sqrt(
                ((touchX - positionX) * (touchX - positionX) + 
                 (touchY - charY) * (touchY - charY)).toDouble()
            ).toFloat()
            
            if (distance < 150 * scale && animationState == AnimationState.WALKING) {
                animationState = AnimationState.FALLING
                fallProgress = 0f
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startAnimation() {
        isAnimating = true
        handler.post(updateRunnable)
    }

    private fun stopAnimation() {
        isAnimating = false
        handler.removeCallbacks(updateRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        positionY = height * 0.65f
        
        // Draw the anime-style walking person
        drawAnimePerson(canvas, positionX, positionY, walkCycle, fallProgress)
    }

    private fun drawAnimePerson(canvas: Canvas, x: Float, y: Float, cycle: Int, fall: Float) {
        canvas.save()
        
        // Apply rotation for falling
        val rotation = fall * 90f
        canvas.rotate(rotation, x, y)
        
        // Move down slightly when falling
        val fallOffset = fall * 50f * scale
        canvas.translate(0f, fallOffset)
        
        val headRadius = 35f * scale
        val bodyHeight = 90f * scale
        val armLength = 55f * scale
        val legLength = 70f * scale
        
        // Legs position
        val legsY = y
        val bodyTop = y - bodyHeight
        val headCenterY = bodyTop - headRadius
        
        // Arms and legs animate based on walk cycle (only when walking)
        val legAngle = if (animationState == AnimationState.WALKING) {
            when (cycle) {
                0 -> 35f
                1 -> 15f
                2 -> -35f
                3 -> -15f
                else -> 0f
            }
        } else 0f
        
        val armAngle = -legAngle * 0.7f
        
        // Draw legs first (behind body)
        drawLeg(canvas, x, legsY, legLength, legAngle, true)
        drawLeg(canvas, x, legsY, legLength, -legAngle, false)
        
        // Draw body (torso as rounded rectangle)
        val bodyRect = RectF(
            x - 25f * scale,
            bodyTop,
            x + 25f * scale,
            legsY - 5f * scale
        )
        canvas.drawRoundRect(bodyRect, 15f * scale, 15f * scale, clothesPaint)
        canvas.drawRoundRect(bodyRect, 15f * scale, 15f * scale, outlinePaint)
        
        // Draw arms
        drawArm(canvas, x - 15f * scale, bodyTop + 15f * scale, armLength, armAngle, true)
        drawArm(canvas, x + 15f * scale, bodyTop + 15f * scale, armLength, -armAngle, false)
        
        // Draw head
        drawAnimeHead(canvas, x, headCenterY, headRadius, fall)
        
        canvas.restore()
    }

    private fun drawLeg(canvas: Canvas, x: Float, y: Float, length: Float, angle: Float, isLeft: Boolean) {
        val legX = x + (if (isLeft) -8f else 8f) * scale
        val endX = legX + length * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
        val endY = y + length * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
        
        // Thigh
        outlinePaint.strokeWidth = 16f * scale
        canvas.drawLine(legX, y - 5f * scale, endX, endY, clothesPaint)
        outlinePaint.strokeWidth = 6f * scale
        canvas.drawLine(legX, y - 5f * scale, endX, endY, outlinePaint)
        
        // Foot
        canvas.drawCircle(endX, endY, 8f * scale, fillPaint)
        canvas.drawCircle(endX, endY, 8f * scale, outlinePaint)
    }

    private fun drawArm(canvas: Canvas, x: Float, y: Float, length: Float, angle: Float, isLeft: Boolean) {
        val endX = x + length * Math.sin(Math.toRadians(angle.toDouble())).toFloat()
        val endY = y + length * Math.cos(Math.toRadians(angle.toDouble())).toFloat()
        
        // Arm
        outlinePaint.strokeWidth = 12f * scale
        canvas.drawLine(x, y, endX, endY, clothesPaint)
        outlinePaint.strokeWidth = 6f * scale
        canvas.drawLine(x, y, endX, endY, outlinePaint)
        
        // Hand
        canvas.drawCircle(endX, endY, 10f * scale, fillPaint)
        canvas.drawCircle(endX, endY, 10f * scale, outlinePaint)
    }

    private fun drawAnimeHead(canvas: Canvas, x: Float, y: Float, radius: Float, fall: Float) {
        // Face
        canvas.drawCircle(x, y, radius, fillPaint)
        canvas.drawCircle(x, y, radius, outlinePaint)
        
        // Hair (spiky anime style)
        val hairPath = Path().apply {
            // Top spikes
            moveTo(x - radius * 0.7f, y - radius * 0.3f)
            lineTo(x - radius * 0.5f, y - radius * 1.2f)
            lineTo(x - radius * 0.2f, y - radius * 0.5f)
            lineTo(x, y - radius * 1.3f)
            lineTo(x + radius * 0.2f, y - radius * 0.5f)
            lineTo(x + radius * 0.5f, y - radius * 1.2f)
            lineTo(x + radius * 0.7f, y - radius * 0.3f)
            
            // Sides
            lineTo(x + radius * 0.9f, y)
            lineTo(x + radius * 0.7f, y + radius * 0.3f)
            lineTo(x - radius * 0.7f, y + radius * 0.3f)
            lineTo(x - radius * 0.9f, y)
            close()
        }
        canvas.drawPath(hairPath, hairPaint)
        canvas.drawPath(hairPath, outlinePaint)
        
        // Eyes (big anime eyes)
        val eyeY = y - radius * 0.15f
        val eyeOffset = radius * 0.35f
        
        // Expression changes when falling
        if (fall > 0.3f) {
            // Surprised eyes
            drawAnimeEye(canvas, x - eyeOffset, eyeY, radius * 0.18f, true)
            drawAnimeEye(canvas, x + eyeOffset, eyeY, radius * 0.18f, true)
            
            // Open mouth (surprised)
            canvas.drawCircle(x, y + radius * 0.4f, radius * 0.15f, fillPaint)
            canvas.drawCircle(x, y + radius * 0.4f, radius * 0.15f, outlinePaint)
        } else {
            // Normal happy eyes
            drawAnimeEye(canvas, x - eyeOffset, eyeY, radius * 0.15f, false)
            drawAnimeEye(canvas, x + eyeOffset, eyeY, radius * 0.15f, false)
            
            // Smile
            val smilePath = Path().apply {
                moveTo(x - radius * 0.3f, y + radius * 0.3f)
                quadTo(x, y + radius * 0.5f, x + radius * 0.3f, y + radius * 0.3f)
            }
            outlinePaint.strokeWidth = 4f * scale
            canvas.drawPath(smilePath, outlinePaint)
            outlinePaint.strokeWidth = 6f * scale
        }
        
        // Blush
        canvas.drawCircle(x - radius * 0.65f, y + radius * 0.1f, radius * 0.2f, blushPaint)
        canvas.drawCircle(x + radius * 0.65f, y + radius * 0.1f, radius * 0.2f, blushPaint)
    }

    private fun drawAnimeEye(canvas: Canvas, x: Float, y: Float, size: Float, surprised: Boolean) {
        if (surprised) {
            // Large circle for surprised
            canvas.drawCircle(x, y, size * 1.5f, fillPaint)
            canvas.drawCircle(x, y, size * 1.5f, outlinePaint)
            canvas.drawCircle(x, y, size * 0.8f, eyePaint)
            // Highlight
            canvas.drawCircle(x - size * 0.3f, y - size * 0.3f, size * 0.4f, fillPaint)
        } else {
            // Normal anime eye
            canvas.drawCircle(x, y, size, fillPaint)
            canvas.drawCircle(x, y, size, outlinePaint)
            canvas.drawCircle(x, y + size * 0.2f, size * 0.6f, eyePaint)
            // Highlight
            canvas.drawCircle(x - size * 0.2f, y, size * 0.3f, fillPaint)
        }
    }
}
