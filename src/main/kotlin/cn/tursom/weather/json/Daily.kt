@file:Suppress("SpellCheckingInspection")

package cn.tursom.weather.json

data class Daily(
    val `3hourForcast`: List<`3HourForcast`>,
    val air_press: String,
    val day: String,
    val day_air_temperature: String,
    val day_weather: String,
    val day_weather_code: String,
    val day_weather_pic: String,
    val day_wind_direction: String,
    val day_wind_power: String,
    val jiangshui: String,
    val night_air_temperature: String,
    val night_weather: String,
    val night_weather_code: String,
    val night_weather_pic: String,
    val night_wind_direction: String,
    val night_wind_power: String,
    val sun_begin_end: String,
    val weekday: Int,
    val ziwaixian: String
)