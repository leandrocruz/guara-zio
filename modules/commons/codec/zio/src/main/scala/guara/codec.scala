package guara

import zio.json.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration.Duration
import scala.util.matching.Regex
import domain.*

object codec {

  private val format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

  given JsonDecoder[RequestId]     = JsonDecoder[String].mapOrFail(RequestId.decode)
  given JsonDecoder[Duration]      = JsonDecoder[String].map(Duration.apply)
  given JsonEncoder[Duration]      = JsonEncoder[String].contramap(_.toMillis + "ms")
  given JsonDecoder[LocalDateTime] = JsonDecoder[String].map(ldt => LocalDateTime.parse(ldt, format))

  given JsonEncoder[Origin]             = JsonEncoder[String].contramap(_.value)
  given JsonDecoder[Origin]             = JsonDecoder[String].map(Origin.of)
  given JsonEncoder[UnifiedErrorFormat] = DeriveJsonEncoder.gen

  def safeDecode(regex: Regex, maxLength: Int) = {
    JsonDecoder.string.mapOrFail { str =>
      (str.length > maxLength, regex.matches(str)) match
        case (true, _)  => Left(s"'$str' must have at most $maxLength chars")
        case (_, false) => Left(s"'$str' has invalid chars")
        case (_, true)  => Right(str.trim.replaceAll(" +", " "))
    }
  }

  def safeCode      = safeDecode(code     , _)
  def safeName      = safeDecode(name     , _)
  def safeLatinName = safeDecode(latinName, _)
}
