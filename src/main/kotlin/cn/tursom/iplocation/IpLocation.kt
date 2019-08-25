package cn.tursom.iplocation

import cn.tursom.Config
import cn.tursom.IpConfig
import cn.tursom.iplocation.json.IpData
import cn.tursom.treediagram.environment.Environment
import cn.tursom.treediagram.module.Module
import cn.tursom.treediagram.service.RegisterService
import cn.tursom.treediagram.service.ServiceConnection
import cn.tursom.treediagram.service.ServiceId
import cn.tursom.treediagram.utils.Json
import cn.tursom.treediagram.utils.ModException
import cn.tursom.utils.AsyncFile
import cn.tursom.utils.AsyncHttpRequest
import cn.tursom.utils.bytebuffer.ByteArrayAdvanceByteBuffer
import cn.tursom.utils.cache.DefaultAsyncPotableCacheMap
import cn.tursom.utils.xml.Xml
import cn.tursom.web.HttpContent
import java.util.logging.Level


@RegisterService
@ServiceId("IpToCity", "IpLocation")
class IpLocation : Module() {
  override val modDescription: String = "通过IP地址获取城市"
  override val modHelper: String = ""
  private lateinit var config: IpConfig
  private val cache by lazy { DefaultAsyncPotableCacheMap<String, String>(config.cacheTime) }

  override suspend fun init(user: String?, environment: Environment) {
    super.init(user, environment)
    val configFile = AsyncFile("${uploadPath}WeatherConfig.xml")
    val byteBuffer = ByteArrayAdvanceByteBuffer(configFile.size.toInt())
    configFile.read(byteBuffer)
    val str = byteBuffer.getString()
    config = Xml.parse<Config>(str).Ip
  }

  override suspend fun receiveMessage(message: Any?, environment: Environment): Any {
    return if (message is HttpContent) {
      handle(message, environment)
    } else {
      getCity(message.toString())
    }
  }

  override suspend fun getConnection(connection: ServiceConnection, environment: Environment) {
    while (true) {
      val msg = connection.recv<Any?>() ?: return
      connection.send(receiveMessage(msg, environment))
    }
  }

  override suspend fun handle(content: HttpContent, environment: Environment): Any {
    val ipStr = content.getHeader("X-Forwarded-For") ?: content.clientIp.toString().let { str ->
      str.substring(1, str.indexOf(':').let { if (it < 1) str.length else it - 1 })
    }
    environment.logger.log(Level.INFO, "ip $ipStr require ")
    return getCity(ipStr)
  }

  override suspend fun bottomHandle(content: HttpContent, environment: Environment) {
    if (content.getCacheTag() != null) {
      content.usingCache()
    } else {
      content.setCacheTag("cached")
      super.bottomHandle(content, environment)
    }
  }

  private suspend fun getDataFromNet(ip: String): String {
    return AsyncHttpRequest.getStr(
      config.url, mapOf("ip" to ip), mapOf("Authorization" to "APPCODE ${config.APPCODE}")
    )
  }

  private suspend fun fromIp(ip: String): String {
    return cache.get(ip) { getDataFromNet(ip) }
  }

  suspend fun getCity(ip: String): String {
    val data = Json.gson.fromJson(fromIp(ip), IpData::class.java)
    return if (data.ret == 200) data.data.city else throw ModException("无法获取IP，错误代码${data.ret}：${data.msg}")
  }
}


