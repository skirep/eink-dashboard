package com.eink.launcher.fold

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDrawerPerformanceTest {
    @Test fun thousandApplicationsOnlyCreateVisibleRowsAndRecycleOnScroll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
            assertTrue(UiDevice.getInstance(instrumentation).wait(
                Until.hasObject(By.descContains("Mantén premut per treure el mosaic")), 10000))
            lateinit var list: RecyclerView
            scenario.onActivity { activity ->
                val info = activity.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).first()
                val adapter = AppDrawerAdapter(activity.lifecycleScope, AppCatalog(activity), {}, {})
                adapter.submit((0 until 1000).map { LauncherApp(info, "fixture/$it", "Application $it") }, emptySet())
                list = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    this.adapter = adapter; itemAnimator = null
                }
                activity.setContentView(list)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1000, list.adapter!!.itemCount)
                assertTrue("Only viewport rows should be inflated", list.childCount in 1..40)
                list.scrollToPosition(999)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue(list.childCount in 1..40)
                assertNotNull("The final application remains reachable", list.findViewHolderForAdapterPosition(999))
            }
        }
    }
}
