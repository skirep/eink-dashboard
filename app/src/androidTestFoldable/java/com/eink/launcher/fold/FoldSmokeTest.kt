package com.eink.launcher.fold

import androidx.test.core.app.ActivityScenario
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.ViewPager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            resize("1080x1920")
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista compacta")), 15000))
                assertTrue(device.hasObject(By.text("Cabrils")))
                assertTrue(device.hasObject(By.text("Tria una ciutat")))
                assertTrue(device.wait(Until.hasObject(By.text("Calendar")), 10000))
                screenshot("compact")
                resize("1920x1920")
                assertTrue(device.wait(Until.hasObject(By.textContains("Vista ampla")), 15000))
                screenshot("expanded")
                assertTrue(device.hasObject(By.text("Trucades")))
                assertTrue(device.hasObject(By.text("SMS")))
                assertFalse(device.hasObject(By.text("Aplicacions  ↗")))
                fun findPager(view: View): LauncherPager? {
                    if (view is LauncherPager) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) findPager(view.getChildAt(i))?.let { return it }
                    return null
                }
                val trace = java.util.concurrent.ConcurrentLinkedQueue<String>()
                var gesture = "none"
                fun captureTouches() {
                    scenario.onActivity { activity ->
                        val original = requireNotNull(activity.window.callback)
                        activity.window.callback = object : android.view.Window.Callback by original {
                            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                                val handled = original.dispatchTouchEvent(event)
                                val active = findPager(activity.window.decorView)
                                trace.add("${event.actionMasked}@${event.x.toInt()},${event.y.toInt()}/raw=${event.rawX.toInt()},${event.rawY.toInt()}/$handled/pager=${active?.hashCode()},down=${active?.pointerDown},state=${active?.pagingState},offset=${active?.scrollX}")
                                return handled
                            }
                        }
                    }
                }
                captureTouches()
                fun assertPage(position: Int) {
                    val ready = CountDownLatch(1)
                    var actual = "unavailable"
                    scenario.onActivity { activity ->
                        val check = object : Runnable {
                            override fun run() {
                                val pager = findPager(activity.window.decorView) ?: return
                                actual = "page=${pager.currentItem}, state=${pager.pagingState}, bounds=${pager.width}x${pager.height}, offset=${pager.scrollX}, count=${pager.adapter?.count}, focus=${activity.hasWindowFocus()}, back=${pager.canScrollHorizontally(-1)}"
                                if (activity.hasWindowFocus() && pager.isLaidOut && pager.currentItem == position && pager.pagingState == ViewPager.SCROLL_STATE_IDLE) ready.countDown()
                                else if (!activity.isDestroyed && ready.count > 0) activity.window.decorView.postDelayed(this, 50)
                            }
                        }
                        activity.window.decorView.post(check)
                    }
                    if (!ready.await(10, TimeUnit.SECONDS)) {
                        ready.countDown()
                        screenshot("failed-page-$position")
                        fail("Expected page $position; $actual; $gesture; touches=$trace")
                    }
                }
                fun swipePage(toApps: Boolean) {
                    assertPage(if (toApps) 0 else 1)
                    device.waitForIdle()
                    val bounds = Rect()
                    scenario.onActivity { activity ->
                        val pager = findPager(activity.window.decorView)!!
                        val location = IntArray(2); pager.getLocationOnScreen(location)
                        bounds.set(location[0], location[1], location[0] + pager.width, location[1] + pager.height)
                    }
                    assertTrue(bounds.intersect(0, 0, device.displayWidth, device.displayHeight))
                    val left = bounds.left + bounds.width() / 5
                    val right = bounds.right - bounds.width() / 5
                    gesture = "toApps=$toApps, screen=$bounds, display=${device.displayWidth}x${device.displayHeight}"
                    trace.clear()
                    assertTrue("Touch injection failed: $gesture", device.swipe(if (toApps) right else left, bounds.centerY(), if (toApps) left else right, bounds.centerY(), 18))
                    assertPage(if (toApps) 1 else 0)
                }
                swipePage(true)
                assertTrue(device.findObject(UiSelector().textContains("Camera")).waitForExists(10000))
                screenshot("applications-right-page")
                assertTrue(device.hasObject(By.text("Trucades")))
                assertTrue(device.hasObject(By.text("SMS")))
                swipePage(false)
                assertTrue(device.hasObject(By.text("Cabrils")))
                // Visible header labels work without a gesture and do not rebuild the root.
                device.findObject(UiSelector().description("Pàgina aplicacions")).click()
                assertPage(1)
                val query = "zzzz-test-search"
                assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 10000))
                device.findObject(UiSelector().className("android.widget.EditText")).setText(query)
                resize("1080x1920")
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                scenario.recreate()
                assertTrue(device.wait(Until.hasObject(By.text(query)), 15000))
                assertPage(1)
                screenshot("search-restored")
                // Native paging must also work after compact resize and Activity recreation.
                captureTouches()
                swipePage(false)
                swipePage(true)
                assertTrue(device.hasObject(By.text(query)))
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
