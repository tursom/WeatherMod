@file:Suppress("SpellCheckingInspection")

package cn.tursom.weather.json

data class WeatherJson(
    val showapi_res_body: ShowapiResBody,
    val showapi_res_code: Int,
    val showapi_res_error: String,
    val showapi_res_id: String
)