package cn.tursom.weather

import cn.tursom.Config
import cn.tursom.WeatherConfig
import cn.tursom.database.async.sqlite.AsyncSqliteHelper
import cn.tursom.treediagram.environment.Environment
import cn.tursom.treediagram.environment.ServiceCaller
import cn.tursom.treediagram.module.ModDescription
import cn.tursom.treediagram.module.ModHelper
import cn.tursom.treediagram.module.ModPath
import cn.tursom.treediagram.module.Module
import cn.tursom.treediagram.service.RegisterService
import cn.tursom.treediagram.utils.ModException
import cn.tursom.utils.AsyncFile
import cn.tursom.utils.AsyncHttpRequest
import cn.tursom.utils.background
import cn.tursom.utils.bytebuffer.ByteArrayAdvanceByteBuffer
import cn.tursom.utils.cache.AsyncSoftCacheMap
import cn.tursom.utils.cache.AsyncSqlStringCacheMap
import cn.tursom.utils.fromJson
import cn.tursom.utils.xml.Xml
import cn.tursom.weather.json.Daily
import cn.tursom.weather.json.WeatherJson
import cn.tursom.web.HttpContent
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import java.util.logging.Level
import java.util.logging.Logger

@RegisterService
@ModPath("weather", "weather/:city", "weather/:json/:city")
@ModDescription("获取天气")
@ModHelper("""@param """)
class WeatherMod : Module() {
  private val jp = JsonParser()
  private val bytesCacheMap by lazy { AsyncSoftCacheMap<String, ByteArray>(config.cacheTime) }
  private val stringCacheMap by lazy {
    AsyncSqlStringCacheMap(
      database,
      config.cacheTime,
      table = "weather",
      logger = logger,
      prevCacheMap = AsyncSoftCacheMap(config.cacheTime)
    )
  }
  private val objectCacheMap by lazy { AsyncSoftCacheMap<String, WeatherJson>(config.cacheTime) }
  private val database by lazy { AsyncSqliteHelper("${modPath}weather.db") }
  private var caller: ServiceCaller? = null
  private lateinit var logger: Logger
  private lateinit var config: WeatherConfig

  override suspend fun init(user: String?, environment: Environment) {
    super.init(user, environment)
    logger = environment.logger
    background {
      var loopTime = 0
      while (caller == null) {
        try {
          caller = environment.getCaller(user, "IpToCity")
        } catch (e: Exception) {
          e.printStackTrace()
        }
        delay(100)
        loopTime++
        if (loopTime > 50) return@background
      }
    }
    val configFile = AsyncFile("${uploadPath}WeatherConfig.xml")
    val byteBuffer = ByteArrayAdvanceByteBuffer(configFile.size.toInt())
    configFile.read(byteBuffer)
    val str = byteBuffer.getString()
    config = Xml.parse<Config>(str).Weather
  }

  override suspend fun receiveMessage(message: Any?, environment: Environment): Any? {
    return getObjectData(message.toString(), environment.logger)
  }

  override suspend fun handle(content: HttpContent, environment: Environment): ByteArray {
    val city = content["city"]
      ?: caller?.call(content) as String?
      ?: throw ModException("需要提供城市名")
    return when {
      content["city"] == "json" || content["json"] != null -> getBytesData(city, environment.logger)
      else -> try {
        generateStr(getObjectData(city, environment.logger)).toByteArray()
      } catch (e: Exception) {
        e.printStackTrace()
        "no data".toByteArray()
      }
    }
  }

  override suspend fun bottomHandle(content: HttpContent, environment: Environment) {
    val cacheTime = content.getCacheTag()?.toLongOrNull()
    if (cacheTime != null && cacheTime + config.cacheTime > System.currentTimeMillis()) {
      content.usingCache()
    } else {
      content.setCacheTag(System.currentTimeMillis())
      if (content["city"] == "json" || content["json"] != null) {
        content.finishJson(handle(content, environment))
      } else {
        content.finishText(handle(content, environment))
      }
    }
  }

  private suspend fun getBytesData(city: String, logger: Logger): ByteArray {
    return getStringData(city, logger).toByteArray()
  }

  private suspend fun getStringData(city: String, logger: Logger): String {
    return stringCacheMap.get(city) { updateCache(city, logger) }
  }

  private suspend fun updateCache(city: String, logger: Logger): String {
    logger.log(Level.INFO, "WeatherMod update cache, city: $city")
    return try {
      val str = AsyncHttpRequest.getStr(
        config.url,
        mapOf(
          "area" to city,
          "need3HourForcast" to "1",
          "needAlarm" to "1",
          "needHourData" to "1",
          "needIndex" to "1",
          "needMoreDay" to "1"
        ),
        mapOf("Authorization" to "APPCODE ${config.APPCODE}")
      )
      val je = jp.parse(str)
      prettyGson.toJson(je)
    } catch (e: Exception) {
      e.printStackTrace()
      "{}"
    }
  }

  private suspend fun getObjectData(city: String, logger: Logger): WeatherJson {
    return objectCacheMap.get(city) { gson.fromJson(getStringData(city, logger)) }
  }

  private fun generateStr(weatherJson: WeatherJson): String {
    val sb = StringBuilder()
    val body = weatherJson.showapi_res_body

    val cityInfo = body.cityInfo
    sb.append(
      "${cityInfo.c9}${cityInfo.c7}${cityInfo.c5}${cityInfo.c3}，邮编${cityInfo.c11}\n经纬：${
      if (cityInfo.longitude > 0) "${cityInfo.longitude}°E" else "${-cityInfo.longitude}°W"}, ${
      if (cityInfo.latitude > 0) "${cityInfo.latitude}°N" else "${-cityInfo.latitude}°S"
      }\n"
    )

    val time = body.time
    sb.append(
      "${time.take(4)}年${
      time.filterIndexed { index, _ -> index == 4 || index == 5 }}月${
      time.filterIndexed { index, _ -> index == 6 || index == 7 }}日${
      time.filterIndexed { index, _ -> index == 8 || index == 9 }}时更新数据\n"
    )

    val today = body.now
    sb.append("${today.temperature_time}：${today.weather}，${today.wind_direction}${today.wind_power}\n")

    appendDate(sb, body.f1)
    appendDate(sb, body.f2)
    appendDate(sb, body.f3)
    appendDate(sb, body.f4)
    appendDate(sb, body.f5)
    appendDate(sb, body.f6)
    appendDate(sb, body.f7)

    return sb.toString()
  }

  private fun appendDate(stringBuilder: StringBuilder, daily: Daily) {
    val dailyStr = daily.day
    stringBuilder.append(
      "${dailyStr.take(4)}年${
      dailyStr.filterIndexed { index, _ -> index == 4 || index == 5 }}月${
      dailyStr.takeLast(2)}日：\n白天：${
      daily.day_weather}，${daily.day_wind_direction}${daily.day_wind_power
      }\n黑天：${daily.night_weather}，${daily.night_wind_direction}${daily.night_wind_power}\n"
    )
  }
}
