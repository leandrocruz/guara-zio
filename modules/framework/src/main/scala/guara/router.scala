package guara.framework.router

import zio.*

import zio.http.*
import zio.http.codec.PathCodec
import zio.http.codec.PathCodec.trailing

trait Router {
  def routes: Routes[Any, Nothing]
}

object Echo {

  def routes = {

    def echo(path: Path, req: Request): Task[Response] = {

      def headers = req.headers.map { h => s""""${h.headerName}": "${h.renderedValue}"""" } mkString (", ")

      for {
        body <- req.body.asString
      } yield Response.json(
        s"""{
           |"method"      : "${req.method}",
           |"path"        : "${req.url.path}",
           |"queryString" : "${req.url.queryParams.encode}",
           |"headers"     : { $headers },
           |"body"        : "$body"
           |}""".stripMargin
      )
    }

    Routes(
      Method.ANY / "i" / "echo"/ trailing -> Handler.fromFunctionZIO[(Path, Request)](echo)
    ).sandbox
  }
}