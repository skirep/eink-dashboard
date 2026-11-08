package com.eink.launcher.fold

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock

/** Some OEM clocks protect the alarms activity even when their launcher is public. */
internal object ClockLauncher {
    internal fun tryCandidates(candidates: Sequence<Intent>, start: (Intent) -> Unit): Boolean {
        for (intent in candidates) {
            try { start(intent); return true }
            catch (_: ActivityNotFoundException) { /* Try the next installed clock. */ }
            catch (_: SecurityException) { /* A protected OEM activity must not close Home. */ }
        }
        return false
    }

    fun open(context: Context): Boolean = tryCandidates(sequence {
        yield(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        // Use public launcher intents rather than guessing private activity names.
        for (pkg in listOf("com.hihonor.deskclock", "com.huawei.deskclock",
            "com.android.deskclock", "com.google.android.deskclock")) {
            val launch = try { context.packageManager.getLaunchIntentForPackage(pkg) }
                catch (_: SecurityException) { null }
            if (launch != null) yield(launch)
        }
    }) { context.startActivity(it) }
}
