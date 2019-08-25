package cn.tursom.weather.json

data class HourData(
    val aqi: String,
    val aqiDetail: AqiDetail,
    val sd: String,
    val temperature: String,
    val temperature_time: String,
    val weather: String,
    val weather_code: String,
    val weather_pic: String,
    val wind_direction: String,
    val wind_power: String
)