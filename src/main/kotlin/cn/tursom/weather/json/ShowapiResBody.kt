@file:Suppress("SpellCheckingInspection")

package cn.tursom.weather.json

data class ShowapiResBody(
    val cityInfo: CityInfo,
    val f1: Daily,
    val f2: Daily,
    val f3: Daily,
    val f4: Daily,
    val f5: Daily,
    val f6: Daily,
    val f7: Daily,
    val hourDataList: List<HourData>,
    val now: Now,
    val ret_code: Int,
    val time: String
)