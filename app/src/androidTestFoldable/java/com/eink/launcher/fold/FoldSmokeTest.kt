package com.eink.launcher.fold

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FoldSmokeTest {
    @Test fun resizingKeepsLauncherAndSearchAlive() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val screenshots = File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        fun resize(size: String) {
            device.executeShellCommand("wm size $size")
            device.waitForIdle()
        }
        device.executeShellCommand("wm density 420")
        try {
            resize("1080x2340")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista compacta")), 15000))
                device.takeScreenshot(File(screenshots, "compact.png"))
                resize("2100x2340")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 15000))
                device.takeScreenshot(File(screenshots, "expanded.png"))
                requireNotNull(device.wait(Until.findObject(By.textContains("Totes les aplicacions")), 10000)).click()
                val query = "zzzz-test-search"
                requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10000)).text = query
                resize("1080x2340")
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                device.takeScreenshot(File(screenshots, "search-restored.png"))
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
