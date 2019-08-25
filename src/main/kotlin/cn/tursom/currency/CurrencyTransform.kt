package cn.tursom.currency

import cn.tursom.Config
import cn.tursom.CurrencyConvertConfig
import cn.tursom.currency.convert.ConvertResult
import cn.tursom.currency.convert.ResultX
import cn.tursom.currency.countries.Countries
import cn.tursom.database.async.sqlite.AsyncSqliteHelper
import cn.tursom.treediagram.environment.Environment
import cn.tursom.treediagram.module.ModDescription
import cn.tursom.treediagram.module.ModHelper
import cn.tursom.treediagram.module.ModPath
import cn.tursom.treediagram.module.Module
import cn.tursom.utils.AsyncFile
import cn.tursom.utils.AsyncHttpRequest
import cn.tursom.utils.bytebuffer.ByteArrayAdvanceByteBuffer
import cn.tursom.utils.cache.AsyncSqlStringCacheMap
import cn.tursom.utils.fromJson
import cn.tursom.utils.xml.Xml
import cn.tursom.web.HttpContent
import java.util.logging.Level
import java.util.logging.Logger
import com.google.gson.JsonParser


@ModPath(
  "CurrencyTransform",
  "CurrencyTransform/:from",
  "CurrencyTransform/:from/:to",
  "CurrencyTransform/:from/:to/:amount"
)
@ModDescription("汇率转换")
@ModHelper(
  """from => 源货币
  to => 目的货币，不提供则只提供源货币的汇率
  amount => 数量，默认为0"""
)
class CurrencyTransform : Module() {
  private val database by lazy { AsyncSqliteHelper("${modPath}currency.db") }
  private val convertCacheMap by lazy { AsyncSqlStringCacheMap(database, config.cacheTime, "convert") }
  private val currencyCacheMap by lazy { AsyncSqlStringCacheMap(database, config.cacheTime, "currency") }

  lateinit var countries: Map<String, String>
  private lateinit var logger: Logger
  private lateinit var config: CurrencyConvertConfig

  override suspend fun init(user: String?, environment: Environment) {
    super.init(user, environment)
    this.logger = environment.logger
    val configFile = AsyncFile("${uploadPath}WeatherConfig.xml")
    val byteBuffer = ByteArrayAdvanceByteBuffer(configFile.size.toInt())
    configFile.read(byteBuffer)
    val str = byteBuffer.getString()
    config = Xml.parse<Config>(str).CurrencyConvert
    val countries = HashMap<String, String>()
    gson.fromJson<Countries>(
      AsyncHttpRequest.getStr(config.countries, headers = mapOf("Authorization" to "APPCODE ${config.APPCODE}"))
    ).result.forEach { countries[it.name] = it.currency }
    this.countries = countries
  }

  override suspend fun handle(content: HttpContent, environment: Environment): Any? {
    val from = content["from"]
    val to = content["to"]
    val amount = content["amount"]?.toFloatOrNull()

    environment.logger.log(Level.INFO, "client ${content.clientIp} require $from to $to amount $amount")

    return when {
      from == null -> countries
      to == null -> handleSingle(from)
      amount == null ->
        gson.fromJson<ConvertResult>(convertCacheMap.get("${countries[from] ?: from} to ${countries[to] ?: to}") {
          AsyncHttpRequest.getStr(
            config.convert,
            mapOf("from" to (countries[from] ?: from), "to" to (countries[to] ?: to), "amount" to "1"),
            mapOf("Authorization" to "APPCODE ${config.APPCODE}")
          )
        }).result
      else -> convert(from, to, amount)
    }
  }

  override suspend fun bottomHandle(content: HttpContent, environment: Environment) {
    val cacheTime = content.getCacheTag()?.toLongOrNull()
    if (cacheTime != null && cacheTime + 1000 * 60 * 60 > System.currentTimeMillis()) {
      content.usingCache()
    } else {
      content.setCacheTag(System.currentTimeMillis())
      val result = handle(content, environment)
      when (result) {
        is String ->
          content.finishJson(result.toByteArray())
        else ->
          content.finishJson(prettyGson.toJson(result).toByteArray())
      }
    }
  }

  private suspend fun handleSingle(currency: String): String {
    return currencyCacheMap.get(countries[currency] ?: currency) {
      val str = AsyncHttpRequest.getStr(
        config.singleCurrency,
        mapOf("currency" to (countries[currency] ?: currency)),
        mapOf("Authorization" to "APPCODE ${config.APPCODE}")
      )
      val jp = JsonParser()
      val je = jp.parse(str)
      prettyGson.toJson(je)
    }
  }

  private suspend fun convert(from: String, to: String, amount: Float): ResultX {
    val str = AsyncHttpRequest.getStr(
      config.convert,
      mapOf("from" to (countries[from] ?: from), "to" to (countries[to] ?: to), "amount" to amount.toString()),
      mapOf("Authorization" to "APPCODE ${config.APPCODE}")
    )
    return gson.fromJson<ConvertResult>(str).result
  }
}