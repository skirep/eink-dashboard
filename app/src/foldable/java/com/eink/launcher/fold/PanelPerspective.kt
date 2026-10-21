package com.eink.launcher.fold

import android.content.Context
import android.hardware.*
import android.os.Handler
import android.os.Looper
import android.view.*

internal class TiltFilter {
    var x = 0f; private set
    var y = 0f; private set
    fun step(pitch: Float, roll: Float) {
        val targetX = (-pitch * .18f).coerceIn(-3.5f, 3.5f)
        val targetY = (roll * .18f).coerceIn(-3.5f, 3.5f)
        x += (targetX - x) * .14f; y += (targetY - y) * .14f
    }
    fun reset() { x = 0f; y = 0f }
}

/** One sensor subscription for the visible home; transforms never rebuild its layout. */
internal class PanelPerspective(private val context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    val available get() = sensor != null
    private val handler = Handler(Looper.getMainLooper())
    private val tiles = mutableListOf<MetroTile>()
    private val matrix = FloatArray(9)
    private val remapped = FloatArray(9)
    private val reference = FloatArray(9)
    private val angles = FloatArray(3)
    private var calibrated = false
    private var rotation = -1
    private var pitch = 0f
    private var roll = 0f
    private val filter = TiltFilter()
    private var active = false
    private var touching = false
    private var lastPolicyCheck = 0L
    private val frame = object : Runnable {
        override fun run() {
            if (!active) return
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastPolicyCheck > 1000) {
                lastPolicyCheck = now
                if ((context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode ||
                    (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled())) {
                    enable(false); return
                }
            }
            if (!touching) {
                filter.step(pitch, roll)
                tiles.forEach { apply(it, filter.x, filter.y) }
            }
            handler.postDelayed(this, 33)
        }
    }
    fun bind(root: View) {
        tiles.forEach { apply(it, 0f, 0f) }; tiles.clear()
        fun visit(view: View) {
            if (view is MetroTile) tiles += view
            else if (view is ViewGroup) (0 until view.childCount).forEach { visit(view.getChildAt(it)) }
        }
        visit(root); recalibrate()
    }
    fun touch(down: Boolean) { touching = down }
    fun recalibrate() {
        calibrated = false; pitch = 0f; roll = 0f; filter.reset()
        tiles.forEach { apply(it, 0f, 0f) }
    }
    fun enable(enabled: Boolean) {
        val wanted = enabled && available
        if (wanted == active) return
        active = false; manager.unregisterListener(this); handler.removeCallbacks(frame); recalibrate()
        if (wanted && manager.registerListener(this, sensor, 33333)) { active = true; handler.post(frame) }
    }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active || touching) return
        val displayRotation = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        if (displayRotation != rotation) { rotation = displayRotation; calibrated = false }
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val axes = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, remapped)
        if (!calibrated) { remapped.copyInto(reference); calibrated = true; return }
        SensorManager.getAngleChange(angles, remapped, reference)
        pitch = Math.toDegrees(angles[1].toDouble()).toFloat()
        roll = Math.toDegrees(angles[2].toDouble()).toFloat()
        if (!pitch.isFinite() || !roll.isFinite()) { calibrated = false; pitch = 0f; roll = 0f }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    companion object {
        fun apply(view: View, x: Float, y: Float) {
            val density = view.resources.displayMetrics.density
            view.cameraDistance = 8000 * density
            view.rotationX = x.coerceIn(-3.5f, 3.5f)
            view.rotationY = y.coerceIn(-3.5f, 3.5f)
            view.translationX = y.coerceIn(-3.5f, 3.5f) * density * .6f
            view.translationY = -x.coerceIn(-3.5f, 3.5f) * density * .6f
        }
    }
}
