package guara

import io.circe.*
import io.circe.generic.semiauto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import domain.*

object codec {

  private val format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

  given Decoder[RequestId]     = Decoder[String].emap(RequestId.decode)
  given Encoder[RequestId]     = Encoder[String].contramap(_.id)
  given Decoder[Duration]      = Decoder[String].map(Duration.apply)
  given Encoder[Duration]      = Encoder[String].contramap(_.toMillis + "ms")
  given Decoder[LocalDateTime] = Decoder[String].map(ldt => LocalDateTime.parse(ldt, format))
  given Encoder[LocalDateTime] = Encoder[String].contramap(format.format)

  given Encoder[Origin]             = Encoder[String].contramap(_.value)
  given Decoder[Origin]             = Decoder[String].map(Origin.of)
  given Encoder[UnifiedErrorFormat] = deriveEncoder
  given Decoder[UnifiedErrorFormat] = deriveDecoder
}
