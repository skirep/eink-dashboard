package com.eink.launcher.model

import android.graphics.drawable.Drawable

/**
 * Holds the metadata for a single installed application.
 *
 * @param packageName The Android package name, used to launch the app.
 * @param label       The human-readable application label.
 * @param icon        The application icon drawable.
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable
)
