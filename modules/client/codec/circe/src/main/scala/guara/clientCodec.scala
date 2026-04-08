package guara.http.client.codec.circe

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{decode as circeDecode}
import guara.http.client.*

given encoderFromCirce[T](using enc: Encoder[T]): RequestEncoder[T]  = (value: T)     => Right(value.asJson.noSpaces)
given decoderFromCirce[T](using dec: Decoder[T]): ResponseDecoder[T] = (text: String) => circeDecode[T](text).left.map(_.getMessage)
