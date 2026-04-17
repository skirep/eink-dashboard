package com.eink.launcher.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * SlideshowImageLoader – singleton that manages the list of slideshow images.
 *
 * Images are read from [SLIDESHOW_DIR] inside the app's external files directory
 * (`Context.getExternalFilesDir(null)/EInkLauncher`).  Only JPEG and PNG files
 * are considered.  The current index is kept in memory; when [advance] is called
 * the index moves forward cyclically.
 *
 * Thread safety: all public methods must be called from the main thread.
 */
class SlideshowImageLoader private constructor(private val context: Context) {

    companion object {
        /** Sub-folder name inside the app's external files directory. */
        const val SLIDESHOW_DIR = "EInkLauncher"

        private val SUPPORTED_EXTENSIONS = setOf("jpg", "jpeg", "png")

        @Volatile
        private var instance: SlideshowImageLoader? = null

        fun getInstance(context: Context): SlideshowImageLoader =
            instance ?: synchronized(this) {
                instance ?: SlideshowImageLoader(context.applicationContext).also { instance = it }
            }
    }

    private val imageFiles: List<File> = loadImageFiles()
    private var currentIndex: Int = 0

    /** Returns the current [Bitmap], or null when no images are available. */
    fun currentBitmap(): Bitmap? {
        if (imageFiles.isEmpty()) return null
        return decodeBitmap(imageFiles[currentIndex])
    }

    /** Moves to the next image, wrapping around at the end. */
    fun advance() {
        if (imageFiles.isNotEmpty()) {
            currentIndex = (currentIndex + 1) % imageFiles.size
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun loadImageFiles(): List<File> {
        // Use getExternalFilesDir to avoid needing broad storage permissions on
        // Android 10+ (API 29+).  The path is:
        //   <external storage>/Android/data/com.eink.launcher/files/EInkLauncher/
        val dir = File(context.getExternalFilesDir(null), SLIDESHOW_DIR)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXTENSIONS }
            ?.sortedBy { it.name }
            ?: emptyList()
    }

    private fun decodeBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            null
        }
    }
}
