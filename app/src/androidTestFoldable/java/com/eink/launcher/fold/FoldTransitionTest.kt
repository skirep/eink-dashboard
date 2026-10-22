package com.eink.launcher.fold

import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FoldTransitionTest {
    @Test fun openingAndClosingAnimateThenRestoreCrispUsableViews() = exercise(false)
    @Test fun touchInterruptsAndDisabledEffectsSkipTheTransition() = exercise(true)

    private fun exercise(interrupt: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences("fold-metro", 0)
        val original = prefs.all
        val device = UiDevice.getInstance(instrumentation)
        val animatorScale = device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            // Slow native animations for reliable observation/capture, without changing app timing.
            device.executeShellCommand("settings put global animator_duration_scale 3")
            prefs.edit().putBoolean("effects", true).putBoolean("foldTransition", true)
                .putBoolean("perspective", false).putString("theme", LauncherThemes.METRO).commit()
            device.executeShellCommand("wm density 200")
            device.executeShellCommand("wm size 540x1080")
            device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.desc("Configuració")), 10000))
                fun awaitState(label: String, condition: (FoldActivity) -> Boolean) {
                    val end = System.currentTimeMillis() + 10000
                    var matched = false
                    while (!matched && System.currentTimeMillis() < end) {
                        scenario.onActivity { matched = condition(it) }
                        if (!matched) android.os.SystemClock.sleep(20)
                    }
                    var state = ""
                    scenario.onActivity { state = "starts=${it.foldTransition.starts}, running=${it.foldTransition.running}, progress=${it.foldTransition.progress}, expanded=${it.foldTransition.lastExpanded}, width=${it.resources.configuration.screenWidthDp}, focus=${it.hasWindowFocus()}, animations=${android.animation.ValueAnimator.areAnimatorsEnabled()}" }
                    if (!matched) device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/fold-transition-failure.png")
                    assertTrue("$label ($state)", matched)
                }
                fun assertClean() {
                    awaitState("Transition should complete", { !it.foldTransition.running })
                    scenario.onActivity { activity ->
                        val scene = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
                        assertNull("Snapshot overlay must be released", scene.findViewWithTag<View>("fold-transition-overlay"))
                        val root = scene.getChildAt(scene.childCount - 1)
                        assertEquals(1f, root.alpha, .001f)
                        assertEquals(1f, root.scaleX, .001f); assertEquals(1f, root.scaleY, .001f)
                        assertEquals(1f, activity.foldTransition.progress, .001f)
                    }
                }
                device.executeShellCommand("wm size 960x1080")
                awaitState("Opening must start an actual animated transition", {
                    it.foldTransition.running && it.foldTransition.lastExpanded == true && it.foldTransition.progress < .95f
                })
                if (interrupt) {
                    // A real tap must remove the blurred snapshot before normal hit testing.
                    device.click(8, device.displayHeight / 2)
                    assertClean()
                    device.findObject(UiSelector().description("Pàgina aplicacions")).click()
                    assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                    device.pressBack()
                } else {
                    device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/fold-opening-blur.png")
                    assertClean()
                    device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/fold-open-crisp.png")
                }
                if (interrupt) {
                    // Exercise the real setting, then prove that a viewport change creates no animation.
                    device.findObject(UiSelector().description("Configuració")).click()
                    UiScrollable(UiSelector().className("android.widget.ListView")).scrollTextIntoView("Transició en plegar: activada")
                    device.findObject(UiSelector().text("Transició en plegar: activada")).click()
                    assertFalse(prefs.getBoolean("foldTransition", true))
                    var starts = 0
                    scenario.onActivity { starts = it.foldTransition.starts }
                    device.executeShellCommand("wm size 540x1080")
                    assertTrue(device.wait(Until.hasObject(By.textContains("Vista compacta")), 10000))
                    scenario.onActivity { assertEquals(starts, it.foldTransition.starts) }
                    assertClean()
                    prefs.edit().putBoolean("foldTransition", true).putBoolean("effects", false).commit()
                    device.executeShellCommand("wm size 960x1080")
                    assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 10000))
                    scenario.onActivity { assertEquals(starts, it.foldTransition.starts) }
                } else {
                    device.executeShellCommand("wm size 540x1080")
                    awaitState("Closing must animate in the other direction", {
                        it.foldTransition.running && it.foldTransition.lastExpanded == false && it.foldTransition.progress < .95f
                    })
                    device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/fold-closing-blur.png")
                    assertClean()
                    device.findObject(UiSelector().description("Pàgina aplicacions")).click()
                    assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                }
            }
        } finally {
            val edit = prefs.edit().clear()
            original.forEach { (key, value) -> when (value) {
                is String -> edit.putString(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            } }
            edit.commit(); LauncherThemes.select(original["theme"] as? String)
            device.executeShellCommand(if (animatorScale == "null") "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $animatorScale")
            device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset")
        }
    }
}
