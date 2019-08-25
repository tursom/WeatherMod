package cn.tursom

data class IpConfig(val APPCODE: String, val url: String, val cacheTime: Long)

data class CurrencyConvertConfig(
  val APPCODE: String,
  val countries: String,
  val singleCurrency: String,
  val convert: String,
  val cacheTime: Long
)

data class WeatherConfig(val APPCODE: String, val url: String, val cacheTime: Long)

@Suppress("SpellCheckingInspection")
data class Config(
  val Weather: WeatherConfig,
  val Ip: IpConfig,
  val CurrencyConvert: CurrencyConvertConfig
)