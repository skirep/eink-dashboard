package com.eink.launcher.model

/**
 * Represents a note item in the home screen notes widget.
 */
data class NoteItem(
    val text: String,
    val isCompleted: Boolean = false
)
