package guara

object http {

  import zio.*
  import zio.http.*
  import scala.util.{Try, Success, Failure}

  def urlFrom(raw: String) = ZIO.fromEither(URL.decode(raw)).mapError(err => Exception(s"Error parsing '$raw'"))

  object client {

    object headers {
      val applicationJson = Header.ContentType(MediaType.application.json)
    }

    trait RequestEncoder[T] {
      def encode(value: T): Either[String, String]
    }

    trait ResponseDecoder[T] {
      def decode(text: String): Either[String, T]
    }

    trait ResponseHandler[T] {
      def handle(response: Response): Task[T]
    }

    object handlers {

      val asText: ResponseHandler[String] = (res: Response) => res.body.asString

      def asTextIf(expected: Int): ResponseHandler[String] = (res: Response) => {
        for
          _    <- ZIO.when(res.status.code != expected) { ZIO.fail(Exception(s"Status code is ${res.status.code}. Expected $expected")) }
          text <- asText.handle(res)
        yield text
      }

      def asDecoded[T](using dec: ResponseDecoder[T]): ResponseHandler[T] = (res: Response) => {
        for
          text  <- res.body.asString
          value <- ZIO.fromEither(dec.decode(text)).mapError(msg => Exception(s"Error decoding response: '$msg'"))
        yield value
      }

      def asDecodedIf[T](expected: Int)(using dec: ResponseDecoder[T]): ResponseHandler[T] = (res: Response) => {
        for
          _     <- ZIO.when(res.status.code != expected) { ZIO.fail(Exception(s"Status code is ${res.status.code}. Expected $expected")) }
          value <- asDecoded.handle(res)
        yield value
      }
    }

    object GuaraHttpClient {

      import zio.http.netty.NettyConfig

      /** Default Client ZLayer: no connection pooling, fast shutdown, default DNS resolver. */
      val layer: ZLayer[Any, Throwable, Client] =
        (ZLayer.succeed(ZClient.Config.default.disabledConnectionPool) ++ ZLayer.succeed(NettyConfig.defaultWithFastShutdown) ++ DnsResolver.default) >>> Client.live

      /** Build a GuaraHttpClient from a base URL and fixed headers, using the default Client layer. */
      def make(base: URL, fixed: Headers = Headers.empty): ZIO[Client & Scope, Nothing, GuaraHttpClient] =
        for
          client <- ZIO.service[Client]
          scope  <- ZIO.service[Scope]
        yield GuaraHttpClient(base, fixed, client, scope)
    }

    /** Intermediate result after encoding the request body — call `.as[T]` to decode the response. */
    class EncodedRequest(client: GuaraHttpClient, request: Request) {
      def as[T](using handler: ResponseHandler[T]): Task[T] = client.execute(request)
    }

    case class GuaraHttpClient(base: URL, fixed: Headers, zClient: Client, scope: Scope) {

      private def perform(request: Request): Task[Response] =
        ZClient.request(request).provide(ZLayer.succeed(zClient), ZLayer.succeed(scope))

      def execute[T](request: Request)(using handler: ResponseHandler[T]): Task[T] = {
        for
          res    <- perform(request)
          result <- handler.handle(res)
        yield result
      }

      def get[T](url: URL, extra: Headers = Headers.empty)(using ResponseHandler[T]): Task[T] = execute {
        Request.get(base ++ url).addHeaders(fixed.addHeaders(extra))
      }

      def post[T](url: URL, extra: Headers = Headers.empty)(body: Body)(using ResponseHandler[T]): Task[T] = execute {
        Request.post(base ++ url, body).addHeaders(fixed.addHeaders(extra))
      }

      def postJson[B](url: URL, extra: Headers = Headers.empty)(body: B)(using enc: RequestEncoder[B]): EncodedRequest = {
        val encoded = enc.encode(body).fold(msg => throw Exception(s"Error encoding request: '$msg'"), identity)
        EncodedRequest(this, Request.post(base ++ url, Body.fromString(encoded)).addHeaders(fixed.addHeaders(extra).addHeader(headers.applicationJson)))
      }

      def put[T](url: URL, extra: Headers = Headers.empty)(body: Body)(using ResponseHandler[T]): Task[T] = execute {
        Request.put(base ++ url, body).addHeaders(fixed.addHeaders(extra))
      }

      def putJson[B](url: URL, extra: Headers = Headers.empty)(body: B)(using enc: RequestEncoder[B]): EncodedRequest = {
        val encoded = enc.encode(body).fold(msg => throw Exception(s"Error encoding request: '$msg'"), identity)
        EncodedRequest(this, Request.put(base ++ url, Body.fromString(encoded)).addHeaders(fixed.addHeaders(extra).addHeader(headers.applicationJson)))
      }

      def delete[T](url: URL, extra: Headers = Headers.empty)(using ResponseHandler[T]): Task[T] = execute {
        Request.delete(base ++ url).addHeaders(fixed.addHeaders(extra))
      }
    }
  }
}
