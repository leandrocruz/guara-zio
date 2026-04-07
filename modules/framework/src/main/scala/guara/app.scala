package guara.framework

import zio.*

import background.BackgroundServices
import guara.framework.config.*
import guara.framework.router.Router
import zio.http.Server

trait GuaraApp extends ZIOAppDefault {

  object HttpServer {
    val layer = ZLayer.fromZIO {

      import guara.framework.config.HttpSSLConfig
      import zio.http.*
      import zio.http.SSLConfig.HttpBehaviour
      import zio.http.Server.RequestStreaming

      def toSSLConfig(ssl: HttpSSLConfig) = {
        SSLConfig.fromResource(
          behaviour               = HttpBehaviour.Redirect,
          certPath                = ssl.certificate,
          keyPath                 = ssl.key,
          clientAuth              = None,
          trustCertCollectionPath = None,
          includeClientCert       = false,
        )
      }

      for {
        cfg <- ZIO.service[GuaraConfig]
      } yield Server
        .defaultWith(
          _ .port(cfg.http.port)
            .requestStreaming(RequestStreaming.Disabled(cfg.http.maxRequestSize))
            .maxInitialLineLength(cfg.http.maxLineSize.getOrElse(4096))
            .maxHeaderSize(cfg.http.maxHeaderSize.getOrElse(8192))
            .copy(
              sslConfig = cfg.http.ssl.map(toSSLConfig)
            )
        )
    }.flatten
  }

  private val srv   : ZLayer[GuaraConfig, Throwable, Server]               = HttpServer.layer
  private val basic : ZLayer[Any        , Throwable, GuaraConfig & Server] = GuaraConfig.layer >>> (srv ++ GuaraConfig.layer)

  def services: ZIO[BackgroundServices & Router & Server & GuaraConfig, Throwable, Unit] =
    for
      config <- ZIO.service[GuaraConfig]
      router <- ZIO.service[Router]
      bg     <- ZIO.service[BackgroundServices]
      _      <- ZIO.logInfo(s"Starting Background Services")
      _      <- bg.start.forkDaemon
      _      <- ZIO.logInfo(s"Starting HTTP Server (${config.name} at ${config.http.port})")
      _      <- Server.serve(router.routes)
    yield ()

  def startGuara         : ZIO[BackgroundServices & Router              , Throwable, Unit] = services.provideSomeLayer(basic)
  def startGuaraNoConfig : ZIO[BackgroundServices & Router & GuaraConfig, Throwable, Unit] = services.provideSomeLayer(srv)
}
