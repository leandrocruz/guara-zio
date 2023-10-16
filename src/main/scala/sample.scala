package sample

import guara.GuaraApp
import guara.processor.Processor
import guara.router.Router
import guara.router.Echo

import zio.*
import zio.http.HttpApp
import zio.http.Middleware.CorsConfig
import zio.http.Routes
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.Header
import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}

import zio.logging.LogFormat
import zio.logging.backend.SLF4J

object SampleApp extends GuaraApp {

  object SampleRouter {
    val layer = ZLayer.fromFunction(SampleRouter.apply _)
  }

  case class SampleRouter() extends Router {

    private def allowedOrigin(origin: Origin): Option[Header.AccessControlAllowOrigin] = Some(AccessControlAllowOrigin.All)

    private val allowAll = CorsConfig(
      allowedOrigin = allowedOrigin,
      allowedMethods = AccessControlAllowMethods.All,
    )

    override def routes: HttpApp[Any] = {
      Echo.routes @@ cors(allowAll)
    }
  }

  override val bootstrap = Runtime.removeDefaultLoggers >>> SLF4J.slf4j(LogFormat.colored)

  override val run = startGuara.provide(
    SampleRouter.layer,
    Processor.drop,
  )
}