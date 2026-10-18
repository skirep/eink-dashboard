package com.eink.launcher.fold

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*

internal data class AgendaEvent(val id: Long, val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String, val calendarId: Long = -1L) {
    fun time(): String {
        val format = SimpleDateFormat(if (allDay) "EEE d · 'Tot el dia'" else "EEE d · HH:mm", Locale("ca"))
        // CalendarContract stores all-day boundaries in UTC, not local midnight.
        if (allDay) format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(start))
    }
}

internal object DashboardData {
    fun calendars(context: Context): List<CalendarSource> {
        val result = mutableListOf<CalendarSource>()
        context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, arrayOf(
            CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.VISIBLE
        ), null, null, "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC")?.use { c ->
            while (c.moveToNext()) result += CalendarSource(c.getLong(0), c.getString(1) ?: "Calendari", c.getString(2) ?: "", c.getString(3) ?: "", c.getInt(4), c.getInt(5) != 0)
        }
        return result
    }

    fun agenda(context: Context, selected: Set<Long>? = null): List<AgendaEvent> {
        if (selected?.isEmpty() == true) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now); ContentUris.appendId(uri, now + 7 * 86400000L)
        val events = mutableListOf<AgendaEvent>()
        val filter = if (selected == null) "${CalendarContract.Instances.VISIBLE} = 1" else
            "${CalendarContract.Instances.CALENDAR_ID} IN (${selected.joinToString(",") { "?" }})"
        val args = arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()) + (selected?.map { it.toString() } ?: emptyList())
        context.contentResolver.query(uri.build(), arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_ID
        ), "${CalendarContract.Instances.STATUS} != ? AND $filter", args, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && events.size < 40) {
                events += AgendaEvent(c.getLong(0), c.getString(1) ?: "Sense títol", c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5) ?: "", c.getLong(6))
            }
        }
        return events
    }

    private fun json(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            return connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    fun findCities(query: String): List<JSONObject> {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val results = json("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=5&language=ca&format=json").optJSONArray("results")
            ?: return emptyList()
        return (0 until results.length()).map { results.getJSONObject(it) }
    }

    fun weather(lat: Double, lon: Double): JSONObject = json(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=5"
    )

    fun condition(code: Int): String = when (code) {
        0 -> "Cel serè"
        1, 2 -> "Sol i núvols"
        3 -> "Ennuvolat"
        45, 48 -> "Boira"
        in 51..57 -> "Plugim"
        in 61..67, in 80..82 -> "Pluja"
        in 71..77, 85, 86 -> "Neu"
        in 95..99 -> "Tempesta"
        else -> "Temps variable"
    }
}
