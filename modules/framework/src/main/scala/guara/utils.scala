package guara

import zio.*

object utils {

  import errors.*
  import domain.*
  import codec.{given, *}
  import zio.http.*
  import zio.json.*
  import java.nio.charset.Charset
  import org.apache.commons.lang3.exception.ExceptionUtils

  extension (body: zio.http.Body) {

    def parse[T](logBody: Boolean = false)(using jsonDecoder: JsonDecoder[T], charset: Charset = utf8): Task[T] = {
      for {
        str   <- body.asString(charset)
        value <- str.fromJson[T] match {
                   case Right(value) => ZIO.succeed(value)
                   case Left(err)    => ZIO.fail(new Exception(s"Failure parsing json body: '$err' ${if(logBody) s" (BODY/$charset: $str)" else "" }"))
                 }
      } yield value
    }
  }

  extension (string: String)
    def as[T]: T = string.asInstanceOf[T]

  extension (long: Long)
    def as[T]: T = long.asInstanceOf[T]

  def call(z: ZIO[Client & Scope, Throwable, Response])(using client: Client, scope: Scope): Task[Response] = {
    z.provide(ZLayer.succeed(client), ZLayer.succeed(scope))
  }

  opaque type SafeResponse = Task[Response]

  object SafeResponse {
    extension (sr: SafeResponse)
      def toTask: Task[Response] = sr
  }

  def ensureResponse(task: Task[Response])(using origin: Origin): SafeResponse = {

    def ise(cause: Throwable, trace: Option[StackTrace] = None) = {

      val stack = trace match
        case Some(value) if !value.isEmpty => value.prettyPrint
        case Some(_)                       => ExceptionUtils.getStackTrace(cause)
        case None                          => ExceptionUtils.getStackTrace(cause)

      val response = Response.json(UnifiedErrorFormat(origin, cause.getMessage, Some(stack)).toJson)
      response.copy(status = Status.InternalServerError, headers = response.headers ++ Headers(Header.Custom("Error", cause.getMessage)))
    }

    task
      .sandbox
      .catchAllTrace {
        case (Cause.Fail(ReturnResponseError(response)                , _), _)     => ZIO.succeed(response)
        case (Cause.Fail(ReturnResponseWithExceptionError(_, response), _), _)     => ZIO.succeed(response)
        case (cause                                                       , trace) => ZIO.succeed(ise(cause.squash, Some(trace)))
      }
      .catchAllDefect { err => ZIO.succeed(ise(err)) }
  }

  extension (params: QueryParams)
    def get(name: String): Option[String] = params.getAll(name).headOption

  extension (url: URL)
    def queryParams(params: QueryParams): URL = url.updateQueryParams(_ => params)
}
