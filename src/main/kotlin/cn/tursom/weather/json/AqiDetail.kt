package cn.tursom.weather.json

data class AqiDetail(
    val aqi: String,
    val area: String,
    val co: String,
    val no2: String,
    val num: String,
    val o3: String,
    val o3_8h: String,
    val pm10: String,
    val pm2_5: String,
    val primary_pollutant: String,
    val quality: String,
    val so2: String
)