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
                screenshot("compact")
                resize("2100x2340")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 15000))
                screenshot("expanded")
                requireNotNull(device.wait(Until.findObject(By.textContains("Aplicacions")), 10000)).click()
                val query = "zzzz-test-search"
                requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)).text = query
                resize("1080x2340")
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                screenshot("search-restored")
                // Activity survives actual viewport changes and process-style recreation.
                scenario.onActivity { activity ->
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
