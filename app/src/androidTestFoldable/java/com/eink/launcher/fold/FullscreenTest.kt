package com.eink.launcher.fold

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FullscreenTest {
    @Test fun barsHidePersistAndRestoreWhileAppsAndDialogsRemainUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("fold-metro", 0)
        val original = prefs.getBoolean("fullscreen", false)
        val theme = prefs.getString("theme", null)
        val device = UiDevice.getInstance(instrumentation)
        try {
            prefs.edit().putBoolean("fullscreen", false).putString("theme", LauncherThemes.LINUX).commit()
            device.executeShellCommand("wm size 1080x1920")
            device.executeShellCommand("wm density 420")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                fun awaitBars(visible: Boolean) {
                    assertTrue("Both system bars must change actual visibility", device.wait(Until.hasObject(By.desc("Configuració")), 10000))
                    val deadline = System.currentTimeMillis() + 5000
                    var matches = false
                    var lastState = "no insets"
                    while (!matches && System.currentTimeMillis() < deadline) {
                        instrumentation.waitForIdleSync()
                        scenario.onActivity {
                            val insets = ViewCompat.getRootWindowInsets(it.window.decorView)
                            lastState = "status=${insets?.isVisible(WindowInsetsCompat.Type.statusBars())}, navigation=${insets?.isVisible(WindowInsetsCompat.Type.navigationBars())}"
                            matches = insets != null &&
                                insets.isVisible(WindowInsetsCompat.Type.statusBars()) == visible &&
                                insets.isVisible(WindowInsetsCompat.Type.navigationBars()) == visible
                        }
                        if (!matches) android.os.SystemClock.sleep(50)
                    }
                    assertTrue("Status/navigation bars visibility should be $visible ($lastState)", matches)
                }
                awaitBars(true)
                device.findObject(By.desc("Configuració")).click()
                val toggle = UiScrollable(UiSelector().className("android.widget.ListView"))
                toggle.scrollTextIntoView("Pantalla completa: desactivada")
                device.findObject(By.text("Pantalla completa: desactivada")).click()
                awaitBars(false)
                assertTrue(prefs.getBoolean("fullscreen", false))
                device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/linux-fullscreen-compact.png")
                scenario.recreate(); awaitBars(false)
                device.findObject(By.desc("Pàgina aplicacions")).click()
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                device.findObject(By.clazz("android.widget.EditText")).apply { click(); text = "Chrome" }
                assertTrue(device.hasObject(By.text("Chrome")))
                device.pressBack(); device.pressBack()
                device.executeShellCommand("wm size 1920x1920")
                assertTrue(device.wait(Until.hasObject(By.desc("Arrossega Temps")), 10000))
                awaitBars(false)
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/linux-fullscreen-expanded.png")
                scenario.onActivity { it.themeSettings() }
                device.pressBack(); awaitBars(false)
                scenario.onActivity { it.setFullscreen(false) }
                awaitBars(true)
                scenario.recreate(); awaitBars(true)
            }
        } finally {
            prefs.edit().putBoolean("fullscreen", original).apply {
                if (theme == null) remove("theme") else putString("theme", theme)
            }.commit()
            LauncherThemes.select(theme)
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
        }
    }
}
