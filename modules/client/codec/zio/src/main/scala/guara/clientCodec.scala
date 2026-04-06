package guara

import zio.json.*
import guara.http.client.*

object clientCodecZio {

  given encoderFromZioJson[T](using enc: JsonEncoder[T]): RequestEncoder[T] =
    (value: T) => Right(value.toJson)

  given decoderFromZioJson[T](using dec: JsonDecoder[T]): ResponseDecoder[T] =
    (text: String) => text.fromJson[T]
}
