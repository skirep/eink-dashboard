package com.eink.launcher.fold

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CalendarAndWeatherTest {
    @Test fun changingSecondCityDoesNotOverwriteCabrilsCache() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("weather-isolation-test", 0)
        prefs.edit().clear().commit()
        val store = WeatherStore(prefs)
        try {
            val homeData = JSONObject("{\"current\":{\"temperature_2m\":18}}")
            store.save(WeatherLocations.cabrils, homeData)
            store.setExtra(WeatherLocation("extra", "Barcelona", 41.38, 2.17))
            store.save(store.extra()!!, JSONObject("{\"current\":{\"temperature_2m\":20}}"))
            store.setExtra(WeatherLocation("extra", "Girona", 41.98, 2.82))
            assertEquals(18, store.snapshot("cabrils").data!!.getJSONObject("current").getInt("temperature_2m"))
            assertNull(store.snapshot("extra").data)
            // A delayed response for the old city must be discarded.
            store.save(WeatherLocation("extra", "Barcelona", 41.38, 2.17), homeData)
            assertNull(store.snapshot("extra").data)
            assertEquals("Girona", store.extra()!!.name)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun googleAndOutlookEventsCanBeCombinedAndFiltered() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val resolver = context.contentResolver
        val created = mutableListOf<android.net.Uri>()
        device.executeShellCommand("pm grant ${context.packageName} android.permission.READ_CALENDAR")
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR")
        device.executeShellCommand("wm density 420"); device.executeShellCommand("wm size 2100x2340")
        fun seed(type: String, name: String, title: String, offset: Long): Long {
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, "fold-metro-qa@example.invalid")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, type).build()
            val result = resolver.insert(uri, ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, "fold-metro-qa@example.invalid")
                put(CalendarContract.Calendars.ACCOUNT_TYPE, type)
                put(CalendarContract.Calendars.NAME, name); put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, name)
                put(CalendarContract.Calendars.CALENDAR_COLOR, 0xff72c8d9.toInt())
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, "fold-metro-qa@example.invalid")
                put(CalendarContract.Calendars.VISIBLE, 1); put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            })!!
            created += result
            val id = ContentUris.parseId(result)
            resolver.insert(CalendarContract.Events.CONTENT_URI, ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, id); put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, System.currentTimeMillis() + offset)
                put(CalendarContract.Events.DTEND, System.currentTimeMillis() + offset + 1800000)
                put(CalendarContract.Events.EVENT_TIMEZONE, "Europe/Madrid")
                // Test a provider with an unspecified status as well as a confirmed event.
                if (type != "com.google") put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
            })!!
            return id
        }
        val prefs = context.getSharedPreferences("fold-metro", 0)
        try {
            val google = seed("com.google", "Google · proves", "Reunió de prova Google", 300000)
            val outlook = seed("com.microsoft.office.outlook.USER_ACCOUNT", "Outlook · proves", "Reunió de prova Outlook", 2400000)
            val sources = DashboardData.calendars(context)
            assertEquals("Google", sources.first { it.id == google }.provider)
            assertEquals("Outlook / Microsoft", sources.first { it.id == outlook }.provider)
            val both = DashboardData.agenda(context, setOf(google, outlook))
            assertEquals(setOf(google, outlook), both.map { it.calendarId }.toSet())
            assertEquals(listOf(google), DashboardData.agenda(context, setOf(google)).map { it.calendarId })
            assertTrue(DashboardData.agenda(context, emptySet()).isEmpty())
            prefs.edit().putStringSet("selectedCalendars", setOf(google.toString(), outlook.toString())).commit()
            ActivityScenario.launch(FoldActivity::class.java).use {
                assertTrue(device.wait(Until.hasObject(By.text("Reunió de prova Google")), 15000))
                assertTrue(device.wait(Until.hasObject(By.text("Reunió de prova Outlook")), 15000))
                device.executeShellCommand("mkdir -p /sdcard/Download/fold-metro")
                device.executeShellCommand("screencap -p /sdcard/Download/fold-metro/agenda-test-fixtures.png")
                requireNotNull(device.findObject(By.text("Calendaris"))).click()
                assertTrue(device.wait(Until.hasObject(By.text("Google + Outlook")), 10000))
                device.waitForIdle()
                // Resolve the node at click time: modal accessibility nodes can be recreated.
                device.findObject(UiSelector().textContains("Outlook / Microsoft · Outlook")).click()
                device.findObject(UiSelector().resourceId("android:id/button1")).click()
                assertTrue(device.wait(Until.hasObject(By.text("Google · 1    Outlook · 0")), 10000))
                assertFalse(device.hasObject(By.text("Reunió de prova Outlook")))
                assertTrue(device.hasObject(By.text("Reunió de prova Google")))
            }
        } finally {
            created.forEach { uri -> resolver.delete(uri, null, null) }
            prefs.edit().remove("selectedCalendars").commit()
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            device.executeShellCommand("wm size reset"); device.executeShellCommand("wm density reset")
        }
    }
}
