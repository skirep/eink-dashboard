package com.eink.launcher.model

/**
 * Represents weather information for display in the home screen.
 */
data class WeatherInfo(
    val location: String,
    val currentTemp: Int,
    val condition: String,
    val feelsLike: Int,
    val wind: String,
    val humidity: Int,
    val sunrise: String,
    val sunset: String,
    val forecast: List<DayForecast>
)

data class DayForecast(
    val dayOfWeek: String,  // DM, DC, DJ, DV, DS
    val iconResId: Int,     // Weather icon resource
    val maxTemp: Int,
    val minTemp: Int
)
