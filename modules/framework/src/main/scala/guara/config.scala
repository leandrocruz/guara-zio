package guara

import zio.*

object config {

  import zio.config.*
  import zio.config.magnolia.*
  import zio.config.typesafe.*
  import Config.*

  import zio.http.Header
  import zio.http.Header.{AccessControlAllowOrigin, AccessControlAllowMethods, Origin}
  import zio.http.Middleware.CorsConfig
  import java.io.File

  case class KafkaConsumerConfig(enabled: Boolean, group: String, topic: String)
  case class KafkaConfig(key: String, secret: String, servers: Seq[String], consumer: KafkaConsumerConfig)
  case class HttpSSLConfig(certificate: String, key: String)
  case class HttpConfig(port: Int, maxRequestSize: Int, maxHeaderSize: Option[Int], maxLineSize: Option[Int], ssl: Option[HttpSSLConfig])
  case class MorbidConfig(url: String, magic: String, updateEvery: Duration)
  case class JwtConfig(key: String)
  case class GuaraConfig(name: String, jwt: JwtConfig, morbid: MorbidConfig, http: HttpConfig, kafka: KafkaConfig)

  object GuaraConfig {

    val cors = ZLayer.succeed {
      def allowedOrigin(origin: Origin): Option[Header.AccessControlAllowOrigin] = Some(AccessControlAllowOrigin.All)

      CorsConfig(
        allowedOrigin = allowedOrigin,
        allowedMethods = AccessControlAllowMethods.All,
      )
    }

    val layer = ZLayer {
      TypesafeConfigProvider.fromResourcePath(enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig])
    }

    def from(config: String) = ZLayer { TypesafeConfigProvider.fromHoconString(config, enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig]) }
    def from(file: File)     = ZLayer { TypesafeConfigProvider.fromHoconFile  (file  , enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig]) }
  }
}
