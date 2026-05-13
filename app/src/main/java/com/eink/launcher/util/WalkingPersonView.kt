package com.eink.launcher.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View

/**
 * WalkingPersonView – custom view that draws and animates a walking stick figure.
 * 
 * The animation cycles through different walking poses and moves the character
 * across the screen. Updates are throttled to be e-ink friendly.
 */
class WalkingPersonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF000000.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var positionX = 100f
    private var walkCycle = 0
    private val handler = Handler(Looper.getMainLooper())
    private var isAnimating = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            if (isAnimating) {
                // Update position and walk cycle
                positionX += 15f
                walkCycle = (walkCycle + 1) % 4
                
                // Reset position when reaching the end
                if (positionX > width + 100) {
                    positionX = -100f
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
        
        val centerY = height / 2f
        
        // Draw the walking person
        drawWalkingPerson(canvas, positionX, centerY, walkCycle)
    }

    private fun drawWalkingPerson(canvas: Canvas, x: Float, y: Float, cycle: Int) {
        val headRadius = 25f
        val bodyHeight = 80f
        val armLength = 50f
        val legLength = 60f
        
        // Head
        canvas.drawCircle(x, y - bodyHeight - headRadius, headRadius, paint)
        
        // Body (line)
        canvas.drawLine(x, y - bodyHeight, x, y, paint)
        
        // Arms and legs animate based on walk cycle
        val legAngle = when (cycle) {
            0 -> 30f
            1 -> 10f
            2 -> -30f
            3 -> -10f
            else -> 0f
        }
        
        val armAngle = -legAngle // Arms move opposite to legs
        
        // Left leg
        val leftLegX = x + legLength * Math.sin(Math.toRadians(legAngle.toDouble())).toFloat()
        val leftLegY = y + legLength * Math.cos(Math.toRadians(legAngle.toDouble())).toFloat()
        canvas.drawLine(x, y, leftLegX, leftLegY, paint)
        
        // Right leg (opposite motion)
        val rightLegX = x + legLength * Math.sin(Math.toRadians(-legAngle.toDouble())).toFloat()
        val rightLegY = y + legLength * Math.cos(Math.toRadians(-legAngle.toDouble())).toFloat()
        canvas.drawLine(x, y, rightLegX, rightLegY, paint)
        
        // Left arm
        val armStartY = y - bodyHeight + 15f
        val leftArmX = x + armLength * Math.sin(Math.toRadians(armAngle.toDouble())).toFloat()
        val leftArmY = armStartY + armLength * Math.cos(Math.toRadians(armAngle.toDouble())).toFloat()
        canvas.drawLine(x, armStartY, leftArmX, leftArmY, paint)
        
        // Right arm (opposite motion)
        val rightArmX = x + armLength * Math.sin(Math.toRadians(-armAngle.toDouble())).toFloat()
        val rightArmY = armStartY + armLength * Math.cos(Math.toRadians(-armAngle.toDouble())).toFloat()
        canvas.drawLine(x, armStartY, rightArmX, rightArmY, paint)
    }
}
