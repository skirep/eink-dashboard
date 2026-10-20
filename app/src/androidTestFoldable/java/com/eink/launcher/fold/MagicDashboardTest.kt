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
        val rss = """<rss xmlns:media="http://search.yahoo.com/mrss/"><channel><item><title>Retro &amp; jocs</title><link>https://example.org/article</link><media:thumbnail url="https://example.org/image.jpg"/></item><item><title>Unsafe</title><link>javascript:bad</link></item></channel></rss>"""
        assertEquals(listOf(NewsItem("Retro & jocs", "https://example.org/article", "https://example.org/image.jpg")), NewsFeed.parse(rss))
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Nova notícia</title><link rel="alternate" href="https://example.org/atom"/></entry></feed>"""
        assertEquals("https://example.org/atom", NewsFeed.parse(atom).single().url)
        assertTrue(NewsFeed.parse("<rss><channel/></rss>").isEmpty())
        var rejected = false
        try { NewsFeed.parse("<!DOCTYPE rss><rss/>") } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
    }
    @Test fun mailOnlyAcceptsIndividualGmailOrOutlookNotices() {
        fun notice(pkg: String, summary: Boolean = false): StatusBarNotification {
            val notification = Notification.Builder(context, "fixture").setContentTitle("Correu de prova")
                .setContentText("Dades fictícies de prova").setSmallIcon(android.R.drawable.ic_dialog_email).build()
            if (summary) notification.flags = notification.flags or Notification.FLAG_GROUP_SUMMARY
            return StatusBarNotification(pkg, pkg, 1, "fixture", 1000, 0, notification, android.os.Process.myUserHandle(), null, 1L)
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
            assertTrue(device.wait(Until.hasObject(By.text("Imatge de fons")), 5000))
            device.pressBack()
            assertTrue(device.hasObject(By.text("WhatsApp")))
        }
    }
}
