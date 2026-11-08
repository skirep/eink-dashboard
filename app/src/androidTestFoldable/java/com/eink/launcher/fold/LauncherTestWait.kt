package com.eink.launcher.fold

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue

/** A visible clock does not mean asynchronous app metadata has reached the home tiles. */
internal fun awaitDashboard(scenario: ActivityScenario<FoldActivity>) {
    val deadline = SystemClock.uptimeMillis() + 10000
    var ready = false
    while (!ready && SystemClock.uptimeMillis() < deadline) {
        scenario.onActivity { ready = it.dashboardReady }
        if (!ready) Thread.sleep(50)
    }
    assertTrue("The initial app catalog and pending dashboard updates must settle", ready)
}
