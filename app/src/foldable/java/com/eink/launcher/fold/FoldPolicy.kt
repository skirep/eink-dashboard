package com.eink.launcher.fold

/** Width is a fallback for OEMs that do not expose hinge information, not a hinge sensor. */
object FoldPolicy {
    fun expanded(widthDp: Int, hasHinge: Boolean): Boolean = hasHinge || widthDp >= 600
    fun columns(widthDp: Int): Int = when {
        widthDp >= 480 -> 3
        else -> 2
    }
}
