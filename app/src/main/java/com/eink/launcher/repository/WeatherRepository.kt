package com.eink.launcher.repository

import com.eink.launcher.api.RetrofitClient
import com.eink.launcher.model.DayForecast
import com.eink.launcher.model.WeatherInfo
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository for weather data from OpenWeatherMap API
 * 
 * To use: Get a free API key from https://openweathermap.org/api
 * Then set it in the API_KEY constant below.
 */
class WeatherRepository {
    
    // TODO: Replace with your OpenWeatherMap API key
    // Register for free at: https://openweathermap.org/api
    private val API_KEY = "YOUR_API_KEY_HERE"
    
    private val weatherApi = RetrofitClient.weatherApi
    
    /**
     * Fetches current weather and forecast for the specified city
     */
    suspend fun getWeatherInfo(city: String = "Barcelona,ES"): Result<WeatherInfo> {
        return try {
            if (API_KEY == "YOUR_API_KEY_HERE") {
                return Result.failure(Exception("Please configure your OpenWeatherMap API key"))
            }
            
            // Get current weather
            val currentResponse = weatherApi.getCurrentWeather(city, API_KEY)
            if (!currentResponse.isSuccessful || currentResponse.body() == null) {
                return Result.failure(Exception("Error fetching current weather: ${currentResponse.message()}"))
            }
            
            val current = currentResponse.body()!!
            
            // Get 5-day forecast
            val forecastResponse = weatherApi.getForecast(city, API_KEY)
            if (!forecastResponse.isSuccessful || forecastResponse.body() == null) {
                return Result.failure(Exception("Error fetching forecast: ${forecastResponse.message()}"))
            }
            
            val forecast = forecastResponse.body()!!
            
            // Parse data
            val weatherInfo = WeatherInfo(
                location = "${current.name}, ${current.sys?.let { getCountryName(it) } ?: ""}",
                currentTemp = current.main?.temp?.toInt() ?: 0,
                condition = current.weather?.firstOrNull()?.description?.capitalize() ?: "",
                feelsLike = current.main?.feels_like?.toInt() ?: 0,
                wind = formatWind(current.wind?.speed, current.wind?.deg),
                humidity = current.main?.humidity ?: 0,
                sunrise = formatTime(current.sys?.sunrise),
                sunset = formatTime(current.sys?.sunset),
                forecast = parseForecast(forecast.list ?: emptyList())
            )
            
            Result.success(weatherInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun formatWind(speed: Double?, degrees: Int?): String {
        val direction = when (degrees) {
            in 0..22 -> "N"
            in 23..67 -> "NE"
            in 68..112 -> "E"
            in 113..157 -> "SE"
            in 158..202 -> "S"
            in 203..247 -> "SO"
            in 248..292 -> "O"
            in 293..337 -> "NO"
            in 338..360 -> "N"
            else -> ""
        }
        val kmh = ((speed ?: 0.0) * 3.6).toInt()
        return "$kmh km/h $direction"
    }
    
    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null) return ""
        val format = SimpleDateFormat("HH:mm", Locale.getDefault())
        return format.format(Date(timestamp * 1000))
    }
    
    private fun parseForecast(items: List<com.eink.launcher.api.ForecastItem>): List<DayForecast> {
        // Group by day and take midday forecast
        val dailyForecasts = items
            .filter { it.dt_txt?.contains("12:00:00") == true }
            .take(5)
            .mapIndexed { index, item ->
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_MONTH, index + 1)
                val dayName = getDayAbbreviation(calendar.get(Calendar.DAY_OF_WEEK))
                
                DayForecast(
                    dayOfWeek = dayName,
                    iconResId = getWeatherIcon(item.weather?.firstOrNull()?.icon),
                    maxTemp = item.main?.temp_max?.toInt() ?: 0,
                    minTemp = item.main?.temp_min?.toInt() ?: 0
                )
            }
        
        return dailyForecasts
    }
    
    private fun getDayAbbreviation(dayOfWeek: Int): String {
        return when (dayOfWeek) {
            Calendar.MONDAY -> "DL"
            Calendar.TUESDAY -> "DM"
            Calendar.WEDNESDAY -> "DC"
            Calendar.THURSDAY -> "DJ"
            Calendar.FRIDAY -> "DV"
            Calendar.SATURDAY -> "DS"
            Calendar.SUNDAY -> "DG"
            else -> ""
        }
    }
    
    private fun getWeatherIcon(icon: String?): Int {
        // You can create proper weather icons later
        // For now, return a placeholder
        return android.R.drawable.ic_menu_info_details
    }
    
    private fun getCountryName(sys: com.eink.launcher.api.Sys): String {
        // Could expand this to show full region names
        return "Catalunya"
    }
    
    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
