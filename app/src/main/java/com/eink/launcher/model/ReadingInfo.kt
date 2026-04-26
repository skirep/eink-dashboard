package com.eink.launcher.model

/**
 * Represents currently reading book information.
 */
data class ReadingInfo(
    val title: String,
    val currentPage: Int,
    val totalPages: Int,
    val coverResId: Int? = null
)
