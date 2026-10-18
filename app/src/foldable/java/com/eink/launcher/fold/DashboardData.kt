package com.eink.launcher.fold

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.text.SimpleDateFormat
import java.util.*

internal data class AgendaEvent(val id: Long, val title: String, val start: Long, val end: Long, val allDay: Boolean, val location: String) {
    fun time(): String = if (allDay) "Tot el dia" else SimpleDateFormat("EEE d · HH:mm", Locale("ca")).format(Date(start))
}

internal object DashboardData {
    fun agenda(context: Context): List<AgendaEvent> {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uri, now)
        ContentUris.appendId(uri, now + 7 * 86400000L)
        val events = mutableListOf<AgendaEvent>()
        context.contentResolver.query(uri.build(), arrayOf(
            CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION
        ), "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.STATUS} != ?",
            arrayOf(CalendarContract.Events.STATUS_CANCELED.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext() && events.size < 12) {
                events += AgendaEvent(c.getLong(0), c.getString(1) ?: "Sense títol", c.getLong(2), c.getLong(3), c.getInt(4) == 1, c.getString(5) ?: "")
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

    fun findCity(query: String): JSONObject {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val results = json("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=ca&format=json")
        return results.optJSONArray("results")?.optJSONObject(0) ?: error("No s'ha trobat la ciutat")
    }

    fun weather(lat: Double, lon: Double): JSONObject = json(
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=5"
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
