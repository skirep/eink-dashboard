package com.eink.launcher.fold

import android.content.SharedPreferences
import org.json.JSONObject

internal data class WeatherLocation(val id: String, val name: String, val latitude: Double, val longitude: Double)
internal object WeatherLocations {
    val cabrils = WeatherLocation("cabrils", "Cabrils", 41.5276, 2.3691)
}
internal data class WeatherSnapshot(val data: JSONObject?, val updated: Long, val attempt: Long)

/** A separate cache per city; changing the comparison city never changes Cabrils. */
internal class WeatherStore(private val prefs: SharedPreferences) {
    fun extra(): WeatherLocation? {
        val name = prefs.getString("extraCity", null) ?: return null
        val lat = prefs.getString("extraLat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("extraLon", null)?.toDoubleOrNull() ?: return null
        return WeatherLocation("extra", name, lat, lon)
    }
    fun locations() = listOfNotNull(WeatherLocations.cabrils, extra())
    fun setExtra(location: WeatherLocation?) {
        prefs.edit().apply {
            remove("data_extra"); remove("updated_extra"); remove("attempt_extra")
            if (location == null) { remove("extraCity"); remove("extraLat"); remove("extraLon") }
            else { putString("extraCity", location.name); putString("extraLat", location.latitude.toString()); putString("extraLon", location.longitude.toString()) }
        }.apply()
    }
    fun snapshot(id: String) = WeatherSnapshot(
        runCatching { JSONObject(prefs.getString("data_$id", "")!!) }.getOrNull(),
        prefs.getLong("updated_$id", 0), prefs.getLong("attempt_$id", 0)
    )
    fun attempt(id: String) { prefs.edit().putLong("attempt_$id", System.currentTimeMillis()).apply() }
    fun save(location: WeatherLocation, data: JSONObject) {
        if (locations().none { it == location }) return
        prefs.edit().putString("data_${location.id}", data.toString()).putLong("updated_${location.id}", System.currentTimeMillis()).apply()
    }
    fun migratePreviousCity() {
        if (prefs.getBoolean("dualWeatherMigrated", false)) return
        val name = prefs.getString("city", "Cabrils") ?: "Cabrils"
        if (!name.startsWith("Cabrils", true)) {
            val lat = prefs.getString("lat", null)?.toDoubleOrNull()
            val lon = prefs.getString("lon", null)?.toDoubleOrNull()
            if (lat != null && lon != null) setExtra(WeatherLocation("extra", name, lat, lon))
        }
        prefs.edit().putBoolean("dualWeatherMigrated", true).apply()
    }
}
