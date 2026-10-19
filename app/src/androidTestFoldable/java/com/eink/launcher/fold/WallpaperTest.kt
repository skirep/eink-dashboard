package com.eink.launcher.fold

import android.graphics.*
import android.net.Uri
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WallpaperTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(): File {
        val image = Bitmap.createBitmap(3200, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 3200f, 1800f,
            intArrayOf(0xff183d62.toInt(), 0xffb67965.toInt(), 0xff0c3047.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawPaint(p); p.shader = null
        for (i in 0..4) {
            p.color = intArrayOf(0xff476479.toInt(), 0xff294c61.toInt(), 0xff17394c.toInt(), 0xff112c3d.toInt(), 0xff0b1f30.toInt())[i]
            val path = Path().apply {
                moveTo(0f, 1050f + i * 140f); cubicTo(900f, 520f + i * 120f, 1700f, 1450f, 3200f, 650f + i * 180f)
                lineTo(3200f, 1800f); lineTo(0f, 1800f); close()
            }
            canvas.drawPath(path, p)
        }
        return File(context.cacheDir, "wallpaper-test.png").also {
            it.outputStream().use { output -> image.compress(Bitmap.CompressFormat.PNG, 100, output) }; image.recycle()
        }
    }

    @Test fun importedImageSurvivesRecreationAndBadImports() = runBlocking {
        val store = WallpaperStore(context); store.clear()
        val source = fixture()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        try {
            val imported = store.import(Uri.fromFile(source))
            assertTrue(imported.width <= 2048 && imported.height <= 2048)
            assertEquals(3200f / 1800f, imported.width.toFloat() / imported.height, .01f)
            val invalid = File(context.cacheDir, "not-an-image.txt").apply { writeText("invalid") }
            try {
                var rejected = false
                try { store.import(Uri.fromFile(invalid)) } catch (_: Exception) { rejected = true }
                assertTrue(rejected)
                assertTrue(imported.sameAs(store.load()))
            } finally { invalid.delete() }
            source.delete() // The launcher must no longer depend on the original URI.
            device.executeShellCommand("wm density 420"); device.executeShellCommand("wm size 1080x2340")
            device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                fun awaitPhoto() {
                    val loaded = CountDownLatch(1)
                    scenario.onActivity { activity ->
                        val check = object : Runnable {
                            override fun run() {
                                val scene = activity.findViewById<FrameLayout>(android.R.id.content).getChildAt(0) as? WeatherScene
                                if (scene?.wallpaper != null) loaded.countDown()
                                else if (!activity.isDestroyed) activity.window.decorView.postDelayed(this, 50)
                            }
                        }
                        activity.window.decorView.post(check)
                    }
                    assertTrue("Stored photo must be visible", loaded.await(10, TimeUnit.SECONDS))
                }
                awaitPhoto(); scenario.recreate(); awaitPhoto()
                assertTrue(device.wait(Until.hasObject(By.text("Trucades")), 10000))
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/custom-wallpaper-test-fixture.png")
                scenario.onActivity { it.wallpaperSettings() }
                assertTrue(device.wait(Until.hasObject(By.text("Torna al paisatge original")), 5000))
                device.findObject(UiSelector().text("Torna al paisatge original")).click()
                device.waitForIdle()
            }
            assertNull(store.load())
        } finally {
            store.clear(); source.delete()
            device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset")
        }
    }

    @Test fun photoWeatherOverlayCanBeDisabledWithoutHidingPhoto() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = Bitmap.createBitmap(100, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(0xff974a2c.toInt()) }
        fun render(overlay: Boolean): Bitmap {
            val result = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync {
                WeatherScene(context, 61, false, false, false, image, overlay).apply {
                    layout(0, 0, 300, 500); draw(Canvas(result))
                }
            }
            return result
        }
        val plain = render(false); val rainy = render(true)
        assertFalse(plain.sameAs(rainy))
        // With the overlay disabled, the photo's warm colour must remain visible under the scrim.
        assertTrue(Color.red(plain.getPixel(150, 250)) > Color.blue(plain.getPixel(150, 250)))
        plain.recycle(); rainy.recycle(); image.recycle()
    }
}
