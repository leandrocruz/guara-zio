package guara.sample

import guara.framework.GuaraApp
import guara.framework.background.BackgroundServices
import guara.framework.router.{Echo, Router}
import guara.http.*
import guara.http.errors.ReturnErrorCodeWithMessage
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

object SampleApp extends GuaraApp {

  override val bootstrap = Runtime.removeDefaultLoggers >>> SLF4J.slf4j

  given Origin = Origin.of("SampleApp")

  private def forcedError(request: Request) = ensureResponse {
    ZIO.fail(ReturnErrorCodeWithMessage(0, "Forced Error"))
  }.toTask

  private val ours = Routes(
    Method.GET / "error" -> Handler.fromFunctionZIO[Request](forcedError),
  ).sandbox

  val router = ZLayer.succeed {
    new Router {
      override def routes = Echo.routes ++ ours
    }
  }

  override val run = startGuara.provide(BackgroundServices.none, router)
}
