package com.eink.launcher.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherMap API interface
 * Register for free API key at: https://openweathermap.org/api
 */
interface WeatherApiService {
    
    @GET("weather")
    suspend fun getCurrentWeather(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ca"
    ): Response<OpenWeatherResponse>
    
    @GET("forecast")
    suspend fun getForecast(
        @Query("q") city: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "ca"
    ): Response<ForecastResponse>
}
