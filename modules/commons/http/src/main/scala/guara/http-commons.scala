package guara.http

import guara.http.codec.{*, given}
import guara.http.errors.*
import guara.http.extensions.*
import org.apache.commons.lang3.RandomStringUtils
import zio.*
import zio.http.*
import zio.json.*

import java.time.format.DateTimeFormatter

case class RequestId(id: String) {
  def track = ZIOAspect.annotated("rid", id)
}

object RequestId {
  private val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  def from(id: String)  : Task[RequestId]           = ZIO.succeed(RequestId(id))
  def decode(id: String): Either[String, RequestId] = Right(RequestId(id))

  def gen = {
    for
      now    <- Clock.localDateTime
      suffix <- ZIO.succeed(RandomStringUtils.secure().nextAlphanumeric(50))
      id     <- ZIO.attempt(now.format(format) + suffix) /* Keep it at 64 chars */
    yield RequestId(id)
  }

}

opaque type Origin = String

object Origin {
  def of(origin: String): Origin = origin

  extension (o: Origin)
    def value: String = o
}

opaque type SafeResponse = Task[Response]

object SafeResponse {
  extension (sr: SafeResponse)
    def toTask: Task[Response] = sr
}

case class UnifiedErrorFormat(
  origin  : Origin,
  message : String,
  code    : Option[Int],
  trace   : Option[String]
)

object UnifiedErrorFormat {

  val HeaderName = "x-uef"
  val StatusCode = Status.InternalServerError

  def fromResponse(response: Response): Task[Option[UnifiedErrorFormat]] = {
    if(response.status == StatusCode && response.isJson) {
      for
        either <- response.body.parse[UnifiedErrorFormat]().either
      yield either match
        case Left(_)      => None
        case Right(value) => Some(value)
    } else ZIO.none
  }

  def toResponse(uef: UnifiedErrorFormat) = {
    Response
      .json(uef.toJson)
      .status(StatusCode)
      .addHeaders(Headers(Header.Custom(HeaderName, uef.message)))
  }

  def make(squashed: Throwable, trace: String)(using origin: Origin): Task[Response] = {

    def make(code: Option[Int]) = toResponse(
      UnifiedErrorFormat(origin, squashed.getMessage, code, Some(trace))
    )

    def log(response: Response, message: String) = ZIO.logError(s"$message: $trace").as(response)

    squashed match
      case ReturnResponseError(res)                 => log(res             , s"ReturnResponseError(${res.status.code}/${squashed.getMessage})")
      case ReturnResponseWithExceptionError(e, res) => log(res             , s"ReturnResponseWithExceptionError(${res.status.code}/${e.getMessage})")
      case ReturnErrorCodeWithException(code, e)    => log(make(Some(code)), s"ReturnErrorCodeWithException($code/${e.getMessage})")
      case ReturnErrorCode(code)                    => log(make(Some(code)), s"ReturnErrorCode($code/${squashed.getMessage})")
      case other                                    => log(make(None)      , s"OtherError(${squashed.getMessage})")
  }
}

object headers {
  val applicationJson = Header.ContentType(MediaType.application.json)
}

def ensureResponseWith(handler: Origin ?=> (Throwable, String) => Task[Response])(task: Task[Response])(using Origin): SafeResponse = {
  task
    .sandbox
    .catchAll { cause =>
      if cause.isInterruptedOnly then ZIO.succeed(Response.status(Status.ServiceUnavailable))
      else                            handler(cause.squash, cause.prettyPrint)
    }
}

def ensureResponse(task: Task[Response])(using Origin): SafeResponse = ensureResponseWith(UnifiedErrorFormat.make)(task)

def call(z: ZIO[Client & Scope, Throwable, Response])(using client: Client, scope: Scope): Task[Response] = {
  z.provide(ZLayer.succeed(client), ZLayer.succeed(scope))
}

def urlFrom(raw: String) = ZIO.fromEither(URL.decode(raw)).mapError(err => Exception(s"Error parsing '$raw'"))
