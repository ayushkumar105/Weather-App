package edu.tcu.akumar.weather.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse (
    val coord: Coord,
    val weather: List<Weather>,
    val main: Main,
    val visibility: Int,
    val wind: Wind,
    val rain: Rain?,
    val snow: Snow?,
    val clouds: Clouds,
    val dt: Int,
    val sys: Sys,
    val timezone: Int,
    val id: Int,
    val name: String
    )
