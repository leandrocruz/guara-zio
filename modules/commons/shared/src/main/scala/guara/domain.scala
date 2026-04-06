package guara

import zio.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.nio.charset.Charset
import scala.util.matching.Regex

object domain {

  case class RequestId(id: String) {
    def track = ZIOAspect.annotated("rid", id)
  }

  object RequestId {
    def from(id: String)  : Task[RequestId]           = ZIO.succeed(RequestId(id))
    def decode(id: String): Either[String, RequestId] = Right(RequestId(id))
  }

  opaque type Origin = String

  object Origin {
    def of(origin: String): Origin = origin

    extension (o: Origin)
      def value: String = o
  }

  case class UnifiedErrorFormat(
    origin  : Origin,
    message : String,
    trace   : Option[String]
  )

  val utf8 = Charset.forName("utf8")

  val code      = "[a-zA-Z0-9_]+".r
  val name      = "[\\w.\\- ]+".r
  val latinName = "[À-ſ\\w.\\-&, ()'/]+".r
}
