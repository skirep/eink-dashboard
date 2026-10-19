package com.eink.launcher.fold

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoldSmokeTest {
    @Test fun weatherLandscapePreviewCanSwitchConditions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
        ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
            scenario.onActivity { it.previewWeather() }
            assertTrue(device.wait(Until.hasObject(By.text("SIMULACIÓ · SOL")), 10000))
            listOf("Pluja" to "PLUJA", "Neu" to "NEU", "Nit" to "NIT").forEach { (button, label) ->
                device.findObject(UiSelector().text(button)).click()
                assertTrue(device.wait(Until.hasObject(By.text("SIMULACIÓ · $label")), 5000))
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/landscape-${button.lowercase()}.png")
            }
            device.findObject(UiSelector().resourceId("android:id/button1")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Cabrils")), 10000))
            assertFalse(device.hasObject(By.textContains("SIMULACIÓ")))
        }
    }

    @Test fun resizingKeepsLauncherAndSearchAlive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        // Keep captures outside app-specific storage: Gradle uninstalls the test app afterwards.
        device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
        fun screenshot(name: String) {
            device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/$name.png")
        }
        fun resize(size: String) {
            device.executeShellCommand("wm size $size")
            device.waitForIdle()
        }
        device.executeShellCommand("wm density 420")
        try {
            resize("1080x2340")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista compacta")), 15000))
                assertTrue(device.hasObject(By.text("Cabrils")))
                assertTrue(device.hasObject(By.text("Tria una ciutat")))
                assertTrue(device.wait(Until.hasObject(By.text("Calendar")), 10000))
                screenshot("compact")
                resize("2100x2340")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 15000))
                screenshot("expanded")
                assertTrue(device.hasObject(By.text("Trucades")))
                assertTrue(device.hasObject(By.text("SMS")))
                assertFalse(device.hasObject(By.text("Aplicacions  ↗")))
                val w = device.displayWidth; val h = device.displayHeight
                device.swipe(w / 4, h / 2, w * 3 / 4, h / 2, 25)
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                assertTrue(device.hasObject(By.text("Trucades")))
                assertTrue(device.hasObject(By.text("SMS")))
                device.swipe(w * 3 / 4, h / 2, w / 4, h / 2, 25)
                assertTrue(device.wait(Until.hasObject(By.text("Cabrils")), 10000))
                device.swipe(w / 4, h / 2, w * 3 / 4, h / 2, 25)
                val query = "zzzz-test-search"
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                device.findObject(UiSelector().className("android.widget.EditText")).setText(query)
                resize("1080x2340")
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                screenshot("search-restored")
                assertTrue(device.hasObject(By.text("Trucades")))
                assertTrue(device.hasObject(By.text("SMS")))
                // Activity survives actual viewport changes and process-style recreation.
                scenario.onActivity { activity ->
                    val home = android.content.Intent(android.content.Intent.ACTION_MAIN)
                        .addCategory(android.content.Intent.CATEGORY_HOME)
                        .addCategory(android.content.Intent.CATEGORY_DEFAULT).setPackage(activity.packageName)
                    assertTrue(activity.packageManager.queryIntentActivities(home, 0).any {
                        it.activityInfo.name == FoldActivity::class.java.name && it.activityInfo.exported
                    })
                    assertEquals("MagicFold", activity.packageManager.getApplicationLabel(activity.applicationInfo).toString())
                    assertFalse(activity.isFinishing)
                    assertEquals("com.eink.launcher.foldable", activity.packageName)
                }
            }
        } finally {
            device.executeShellCommand("wm size reset")
            device.executeShellCommand("wm density reset")
        }
    }
}
