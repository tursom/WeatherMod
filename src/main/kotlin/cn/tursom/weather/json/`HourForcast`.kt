package cn.tursom.weather.json

@Suppress("ClassName", "SpellCheckingInspection")
data class `3HourForcast`(
    val hour: String,
    val temperature: String,
    val temperature_max: String,
    val temperature_min: String,
    val weather: String,
    val weather_pic: String,
    val wind_direction: String,
    val wind_power: String
)