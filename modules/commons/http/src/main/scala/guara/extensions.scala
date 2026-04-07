package guara.http.extensions

import guara.http.codec.{*, given}
import guara.shared.*
import zio.*
import zio.http.*
import zio.json.*
import java.nio.charset.Charset

extension (body: zio.http.Body) {
  def parse[T](logBody: Boolean = false)(using jsonDecoder: JsonDecoder[T], charset: Charset = utf8): Task[T] = {
    for
      str   <- body.asString(charset)
      value <- str.fromJson[T] match {
                 case Right(value) => ZIO.succeed(value)
                 case Left(err)    => ZIO.fail(new Exception(s"Failure parsing json body: '$err' ${if(logBody) s" (BODY/$charset: $str)" else "" }"))
               }
    yield value
  }
}

extension (response: Response) {
  def isJson = response.header(Header.ContentType).exists(_.mediaType == MediaType.application.json)
}

extension (params: QueryParams) {
  def get(name: String): Option[String] = params.getAll(name).headOption
}

extension (url: URL) {
  def queryParams(params: QueryParams): URL = url.updateQueryParams(_ => params)
}