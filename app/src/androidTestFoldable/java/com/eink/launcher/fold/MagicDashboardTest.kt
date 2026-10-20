package com.eink.launcher.fold

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Canvas
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MagicDashboardTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun weatherActuallyChangesTheWallpaperPixels() {
        fun draw(code: Int, night: Boolean): Bitmap {
            val bitmap = Bitmap.createBitmap(300, 500, Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync {
                WeatherScene(context, code, night, false).apply { layout(0, 0, 300, 500); draw(Canvas(bitmap)) }
            }
            return bitmap
        }
        val sun = draw(0, false); val cloud = draw(3, false)
        val rain = draw(61, false); val night = draw(0, true); val unknown = draw(-1, false)
        try {
            assertFalse(sun.sameAs(cloud)); assertFalse(cloud.sameAs(rain)); assertFalse(sun.sameAs(night))
            assertFalse(unknown.sameAs(sun))
            val crops = (0..3).map { WeatherBackdrop.source(WeatherBackdrop.image(context), it) }
            assertTrue(crops[0].right < crops[1].left); assertTrue(crops[0].bottom < crops[2].top)
        } finally { listOf(sun, cloud, rain, night, unknown).forEach { it.recycle() } }
    }
    @Test fun rssAndAtomKeepArticleLinksAndRejectUnsafeDocuments() {
        assertEquals(listOf("Tecnologia", "Retro", "Videojocs", "Futbol", "Economia", "Política catalana", "Política espanyola", "Món"), NewsFeed.defaults.map { it.category })
        val rss = """<rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item><title>Retro &amp; jocs</title><link>https://example.org/article</link><media:thumbnail url="https://example.org/image.jpg"/></item><item><title>Unsafe</title><link>javascript:bad</link></item></channel></rss>"""
        assertEquals(listOf(NewsItem("Retro & jocs", "https://example.org/article", "https://example.org/image.jpg")), NewsFeed.parse(rss))
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Nova notícia</title><link rel="alternate" href="https://example.org/atom"/></entry></feed>"""
        assertEquals("https://example.org/atom", NewsFeed.parse(atom).single().url)
        assertTrue(NewsFeed.parse("<rss><channel/></rss>").isEmpty())
        var rejected = false
        try { NewsFeed.parse("<!DOCTYPE rss><rss/>") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        val many = "<rss><channel>" + (1..30).joinToString("") { "<item><title>Prova $it</title><link>https://example.org/$it</link></item>" } + "</channel></rss>"
        assertEquals(20, NewsFeed.parse(many).size)
    }
    @Test fun deniedAlarmActivityFallsBackWithoutClosingHome() {
        val tried = mutableListOf<String>()
        assertTrue(ClockLauncher.tryCandidates(sequenceOf(android.content.Intent("protected"), android.content.Intent("missing"), android.content.Intent("public-clock"))) {
            tried += it.action.orEmpty()
            when (it.action) {
                "protected" -> throw SecurityException("OEM clock permission")
                "missing" -> throw android.content.ActivityNotFoundException()
            }
        })
        assertEquals(listOf("protected", "missing", "public-clock"), tried)
        assertFalse(ClockLauncher.tryCandidates(sequenceOf(android.content.Intent("blocked"))) { throw SecurityException() })
        assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,
            context.packageManager.checkPermission("com.android.alarm.permission.SET_ALARM", context.packageName))
    }
    @Test fun allNewsSectionsAndFullHeadlineListSurviveRecreation() {
        val prefs = context.getSharedPreferences("fold-metro", android.content.Context.MODE_PRIVATE)
        val keys = NewsFeed.defaults.indices.map { "newsAttempt_$it" } + listOf("newsCategory", "news_7")
        val original = keys.associateWith { prefs.all[it] }
        val device = UiDevice.getInstance(instrumentation)
        try {
            prefs.edit().apply {
                NewsFeed.defaults.indices.forEach { putLong("newsAttempt_$it", System.currentTimeMillis()) }
                putInt("newsCategory", 0)
                putString("news_7", org.json.JSONArray().apply {
                    (1..20).forEach { put(org.json.JSONObject().put("title", "Titular de prova $it").put("url", "https://example.org/$it")) }
                }.toString())
            }.commit()
            ActivityScenario.launch(FoldActivity::class.java).use { scenario ->
                val content = UiScrollable(UiSelector().className("android.widget.ScrollView").scrollable(true))
                assertTrue(content.scrollTextIntoView("Notícies · Seccions ▾"))
                assertTrue(content.scrollIntoView(UiSelector().description("Seccions de notícies · llisca per veure-les totes")))
                val strip = device.wait(Until.findObject(By.desc("Seccions de notícies · llisca per veure-les totes")), 5000).visibleBounds
                device.swipe(strip.right - 15, strip.centerY(), strip.left + 15, strip.centerY(), 30)
                scenario.onActivity { activity ->
                    fun pager(v: android.view.View): LauncherPager? = if (v is LauncherPager) v else if (v is android.view.ViewGroup)
                        (0 until v.childCount).firstNotNullOfOrNull { pager(v.getChildAt(it)) } else null
                    assertEquals(0, pager(activity.window.decorView)?.currentItem)
                }
                assertTrue(content.scrollTextIntoView("Notícies · Seccions ▾"))
                device.findObject(By.text("Notícies · Seccions ▾")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Política catalana")), 5000))
                assertTrue(device.hasObject(By.text("Política espanyola")))
                device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/news-sections.png")
                device.findObject(By.text("Món")).click()
                assertTrue(content.scrollTextIntoView("MÓN · TITULARS DESTACATS"))
                scenario.recreate()
                assertTrue(content.scrollTextIntoView("Veure tots els titulars (20)"))
                device.findObject(By.text("Veure tots els titulars (20)")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Món · ARA · Internacional")), 5000))
                assertTrue(UiScrollable(UiSelector().className("android.widget.ListView")).scrollTextIntoView("Titular de prova 20"))
                device.pressBack()
            }
        } finally {
            prefs.edit().apply { original.forEach { (key, value) -> when (value) {
                null -> remove(key)
                is String -> putString(key, value)
                is Long -> putLong(key, value)
                is Int -> putInt(key, value)
            } } }.commit()
        }
    }
    @Test fun mailOnlyAcceptsIndividualGmailOrOutlookNotices() {
        fun notice(pkg: String, summary: Boolean = false): StatusBarNotification {
            val notification = Notification.Builder(context, "fixture").setContentTitle("Correu de prova")
                .setContentText("Dades fictícies de prova").setSmallIcon(android.R.drawable.ic_dialog_email).build()
            if (summary) notification.flags = notification.flags or Notification.FLAG_GROUP_SUMMARY
            return StatusBarNotification(pkg, pkg, 1, "fixture", 1000, 0, 0, notification, android.os.Process.myUserHandle(), 1L)
        }
        assertEquals("Gmail", MailNotifications.notice(notice("com.google.android.gm"))?.provider)
        assertEquals("Outlook", MailNotifications.notice(notice("com.microsoft.office.outlook"))?.provider)
        assertNull(MailNotifications.notice(notice("com.other.app")))
        assertNull(MailNotifications.notice(notice("com.google.android.gm", true)))
    }
    @Test fun clockAndPhotoControlsOpenTheirDestinations() {
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(FoldActivity::class.java).use {
            assertTrue(device.findObject(UiSelector().description("Obre rellotge i alarmes")).waitForExists(10000))
            device.findObject(UiSelector().description("Obre rellotge i alarmes")).click()
            assertTrue(device.wait(Until.gone(By.desc("Obre rellotge i alarmes")), 10000))
            assertNotEquals(context.packageName, device.currentPackageName)
            device.pressBack()
            assertTrue(device.findObject(UiSelector().description("Les meves fotos")).waitForExists(10000))
            device.findObject(UiSelector().description("Les meves fotos")).click()
            assertTrue("Tapping Photos must open its settings after returning from Clock", device.wait(Until.hasObject(By.text("Imatge de fons")), 5000))
            device.pressBack()
            assertTrue(device.hasObject(By.desc("WhatsApp")))
        }
    }
}
