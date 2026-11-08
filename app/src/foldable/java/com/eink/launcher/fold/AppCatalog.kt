package com.eink.launcher.fold

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale

internal object AppSearch {
    private val marks = "\\p{M}+".toRegex()
    fun normalize(value: String): String = marks.replace(
        Normalizer.normalize(value, Normalizer.Form.NFD), ""
    ).lowercase(Locale.ROOT).trim()
}

internal data class LauncherApp(val info: ResolveInfo, val id: String, val label: String) {
    val searchable = AppSearch.normalize(label)
    val initial = label.take(1).uppercase(Locale.getDefault())
}

/** Package metadata is read once per scan, off the UI thread. Icons are bounded and lazy. */
internal class AppCatalog(context: Context) {
    private val context = context.applicationContext
    private val manager = this.context.packageManager
    private val icons = LruCache<String, Drawable.ConstantState>(64)

    suspend fun scan(): List<LauncherApp> = withContext(Dispatchers.IO) {
        manager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { ComponentName(it.activityInfo.packageName, it.activityInfo.name).flattenToString() }
            .map { info -> LauncherApp(info,
                ComponentName(info.activityInfo.packageName, info.activityInfo.name).flattenToString(),
                info.loadLabel(manager).toString()) }
            .sortedWith(compareBy<LauncherApp> { it.searchable }.thenBy { it.id })
    }

    suspend fun icon(app: LauncherApp): Drawable? = withContext(Dispatchers.IO) {
        icons.get(app.id)?.let { return@withContext it.newDrawable(context.resources).mutate() }
        val drawable = runCatching { app.info.loadIcon(manager) }.getOrNull()
        drawable?.constantState?.let { icons.put(app.id, it) }
        drawable
    }

    fun invalidateIcons() = icons.evictAll()
}
