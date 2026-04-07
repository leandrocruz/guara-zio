package guara.sample

import guara.framework.GuaraApp
import guara.framework.background.BackgroundServices
import guara.framework.router.{Echo, Router}
import guara.http.*
import guara.http.extensions.*
import zio.*
import zio.http.*
import zio.json.*
import zio.logging.backend.SLF4J

object SampleApp extends GuaraApp {

  override val bootstrap = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  val router = ZLayer.succeed {
    new Router {
      override def routes = Echo.routes
    }
  }

  override val run = startGuara.provide(BackgroundServices.none, router)
}
