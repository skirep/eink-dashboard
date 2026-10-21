package com.eink.launcher.fold

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeAndPerspectiveTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun perspectiveIsSmoothedBoundedAndReturnsToNeutral() {
        val filter = TiltFilter()
        filter.step(90f, -90f)
        assertTrue(filter.x < 0 && filter.x > -3.5f)
        repeat(100) { filter.step(90f, -90f) }
        assertEquals(-3.5f, filter.x, .01f); assertEquals(-3.5f, filter.y, .01f)
        repeat(100) { filter.step(0f, 0f) }
        assertEquals(0f, filter.x, .01f); assertEquals(0f, filter.y, .01f)
        filter.reset(); assertEquals(0f, filter.x, 0f)
    }

    @Test fun perspectiveActuallyTransformsPanelPixels() {
        instrumentation.runOnMainSync {
            val parent = FrameLayout(context)
            val tile = MetroTile(context, 0xff315677.toInt(), 0xff102030.toInt())
            parent.addView(tile, FrameLayout.LayoutParams(350, 250).apply { leftMargin = 80; topMargin = 80 })
            parent.measure(View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(450, View.MeasureSpec.EXACTLY))
            parent.layout(0, 0, 600, 450)
            val flat = Bitmap.createBitmap(600, 450, Bitmap.Config.ARGB_8888)
            val tilted = Bitmap.createBitmap(600, 450, Bitmap.Config.ARGB_8888)
            parent.draw(Canvas(flat)); PanelPerspective.apply(tile, 3f, -3f); parent.draw(Canvas(tilted))
            assertFalse(flat.sameAs(tilted))
            PanelPerspective.apply(tile, 0f, 0f)
            assertEquals(0f, tile.rotationX, 0f); assertEquals(0f, tile.rotationY, 0f)
            flat.recycle(); tilted.recycle()
        }
    }

    @Test fun terminalThemePersistsAndKeepsClockAppsAndDockAvailable() {
        val prefs = context.getSharedPreferences("fold-metro", android.content.Context.MODE_PRIVATE)
        val original = prefs.getString("theme", null)
        val device = UiDevice.getInstance(instrumentation)
        try {
            prefs.edit().putString("theme", LauncherThemes.METRO).commit()
            device.executeShellCommand("wm size 1080x1920")
            device.executeShellCommand("wm density 420")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                // Exercise the actual Settings entry and theme selector.
                device.findObject(UiSelector().description("Configuració")).click()
                assertTrue(UiScrollable(UiSelector().className("android.widget.ListView")).scrollTextIntoView("Temes i perspectiva"))
                device.findObject(By.text("Temes i perspectiva")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Terminal · MS-DOS / Linux")), 5000))
                device.findObject(By.text("Terminal · MS-DOS / Linux")).click()
                assertTrue(device.wait(Until.hasObject(By.text("C:\\MAGICFOLD> HOME_")), 10000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text("C:\\MAGICFOLD> HOME_")), 10000))
                assertTrue(device.hasObject(By.desc("Obre rellotge i alarmes")))
                assertTrue(device.hasObject(By.desc("Trucades"))); assertTrue(device.hasObject(By.desc("WhatsApp")))
                scenario.onActivity { activity ->
                    fun textViews(view: View): List<TextView> = (if (view is TextView) listOf(view) else emptyList()) +
                        (if (view is ViewGroup) (0 until view.childCount).flatMap { textViews(view.getChildAt(it)) } else emptyList())
                    val labels = textViews(activity.window.decorView)
                    listOf("C:\\MAGICFOLD> HOME_", "Cabrils", "Tecnologia").forEach { label ->
                        val matching = labels.filter { it.text.toString() == label }
                        assertTrue("Missing visible theme content: $label", matching.isNotEmpty())
                        assertTrue("All panel content must use monospace: $label", matching.all { it.typeface == Typeface.MONOSPACE })
                    }
                    val selectedChip = labels.first { it.text.toString() == "Tecnologia" }
                    assertTrue("Selected section needs readable contrast", androidx.core.graphics.ColorUtils.calculateContrast(selectedChip.currentTextColor, 0xff050e09.toInt()) >= 4.5)
                }
                device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/terminal-compact.png")
                device.executeShellCommand("wm size 1920x1920")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 10000))
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/terminal-expanded.png")
                device.findObject(By.desc("Pàgina aplicacions")).click()
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                device.pressBack()
                scenario.onActivity { it.themeSettings() }
                device.findObject(By.text("Metro · paisatge meteorològic")).click()
                assertTrue(device.wait(Until.hasObject(By.text("MagicFold     ·     Magic V2")), 10000))
                assertEquals(LauncherThemes.METRO, prefs.getString("theme", null))
            }
        } finally {
            prefs.edit().apply { if (original == null) remove("theme") else putString("theme", original) }.commit()
            MetroStyle.terminal = LauncherThemes.id(original) == LauncherThemes.TERMINAL
            device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset")
        }
    }
}
