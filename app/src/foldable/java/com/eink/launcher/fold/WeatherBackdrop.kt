package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import com.eink.launcher.R

internal object WeatherBackdrop {
    private var atlas: Bitmap? = null
    fun image(context: Context): Bitmap = atlas ?: requireNotNull(BitmapFactory.decodeResource(context.resources,
        R.drawable.weather_atlas, BitmapFactory.Options().apply { inScaled = false })).also { atlas = it }
    fun quadrant(code: Int, night: Boolean): Int = when {
        night -> 3
        code in 51..99 -> 2
        code >= 3 -> 1
        else -> 0
    }
    fun source(image: Bitmap, index: Int): Rect {
        val halfW = image.width / 2; val halfH = image.height / 2
        val x = (index % 2) * halfW; val y = (index / 2) * halfH
        return Rect(x + 2, y + 2, x + halfW - 2, y + halfH - 2)
    }
    fun soften(image: Bitmap): Bitmap {
        val height = (image.height.toFloat() / image.width * 64).toInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(image, 64, height, true)
        return Bitmap.createScaledBitmap(small, image.width, image.height, true).also { if (small !== image && small !== it) small.recycle() }
    }
}
