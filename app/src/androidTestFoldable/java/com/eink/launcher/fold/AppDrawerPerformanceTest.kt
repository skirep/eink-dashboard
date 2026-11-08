package com.eink.launcher.fold

import android.content.Intent
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDrawerPerformanceTest {
    @Test fun thousandApplicationsOnlyCreateVisibleRowsAndRecycleOnScroll() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            instrumentation.runOnMainSync {
                val info = context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0).first()
                val adapter = AppDrawerAdapter(scope, AppCatalog(context), {}, {})
                adapter.submit((0 until 1000).map { LauncherApp(info, "fixture/$it", "Application $it") }, emptySet())
                val list = RecyclerView(context).apply {
                    layoutManager = LinearLayoutManager(context)
                    this.adapter = adapter; itemAnimator = null
                }
                fun layout() {
                    list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                    list.layout(0, 0, 1080, 1920)
                }
                layout()
                assertEquals(1000, adapter.itemCount)
                assertTrue("Only viewport rows should be inflated", list.childCount in 1..40)
                list.scrollToPosition(999)
                layout()
                assertTrue(list.childCount in 1..40)
                assertNotNull("The final application remains reachable", list.findViewHolderForAdapterPosition(999))
                list.adapter = null
            }
        } finally { scope.cancel() }
    }
}
