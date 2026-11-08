package com.eink.launcher.fold

import android.content.Context
import android.graphics.*
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/** Private, bounded, orientation-correct copy: the original photo and its URI are never retained. */
internal class WallpaperStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "metro-wallpaper.png"))
    companion object { private val gate = Mutex(); private const val MAX_EDGE = 2048 }
    suspend fun load(): Bitmap? = withContext(Dispatchers.IO) {
        gate.withLock { try { file.openRead().use { BitmapFactory.decodeStream(it) } } catch (_: Exception) { null } }
    }
    suspend fun clear() = withContext(Dispatchers.IO) { gate.withLock { file.delete() } }
    suspend fun import(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        gate.withLock {
            val source = File.createTempFile("wallpaper-", ".source", context.cacheDir)
            try {
                (context.contentResolver.openInputStream(uri) ?: throw IOException("No es pot llegir la imatge")).use { input ->
                    source.outputStream().use { output ->
                        val buffer = ByteArray(8192); var total = 0L
                        while (true) {
                            val read = input.read(buffer); if (read < 0) break
                            total += read
                            if (total > 50L * 1024 * 1024) throw IOException("La imatge supera els 50 MB")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                val bitmap = decode(source)
                val output = file.startWrite()
                try {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("No es pot desar la imatge")
                    file.finishWrite(output)
                } catch (error: Exception) { file.failWrite(output); throw error }
                bitmap
            } finally { source.delete() }
        }
    }
    private fun decode(source: File): Bitmap {
        if (Build.VERSION.SDK_INT >= 28) return ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = minOf(1f, MAX_EDGE.toFloat() / max(info.size.width, info.size.height))
            decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Format d’imatge no compatible")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_EDGE) sample *= 2
        val image = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("No es pot obrir la imatge")
        val orientation = try { ExifInterface(source.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) } catch (_: Exception) { 1 }
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(-90f); postScale(-1f, 1f) }
                8 -> setRotate(-90f)
            }
        }
        if (matrix.isIdentity) return image
        return Bitmap.createBitmap(image, 0, 0, image.width, image.height, matrix, true).also { if (it !== image) image.recycle() }
    }
}
