package guara

import guara.errors.ReturnUnifiedError
import zio.*

object config {

  import zio.config.*
  import zio.config.magnolia.*
  import zio.config.typesafe.*
  import Config.*

  import zio.http.Header
  import zio.http.Header.{AccessControlAllowOrigin, AccessControlAllowMethods, Origin}
  import zio.http.Middleware.CorsConfig
  import java.io.File

  case class KafkaConsumerConfig(enabled: Boolean, group: String, topic: String)
  case class KafkaConfig(key: String, secret: String, servers: Seq[String], consumer: KafkaConsumerConfig)
  case class HttpSSLConfig(certificate: String, key: String)
  case class HttpConfig(port: Int, maxRequestSize: Int, maxHeaderSize: Option[Int], maxLineSize: Option[Int], ssl: Option[HttpSSLConfig])
  case class MorbidConfig(url: String, magic: String, updateEvery: Duration)
  case class JwtConfig(key: String)
  case class GuaraConfig(name: String, jwt: JwtConfig, morbid: MorbidConfig, http: HttpConfig, kafka: KafkaConfig)

  object GuaraConfig {

    val cors = ZLayer.succeed {
      def allowedOrigin(origin: Origin): Option[Header.AccessControlAllowOrigin] = Some(AccessControlAllowOrigin.All)

      CorsConfig(
        allowedOrigin = allowedOrigin,
        allowedMethods = AccessControlAllowMethods.All,
      )
    }

    val layer = ZLayer {
      TypesafeConfigProvider.fromResourcePath(enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig])
    }

    def from(config: String) = ZLayer { TypesafeConfigProvider.fromHoconString(config, enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig]) }
    def from(file: File)     = ZLayer { TypesafeConfigProvider.fromHoconFile  (file  , enableCommaSeparatedValueAsList = true).load(deriveConfig[GuaraConfig]) }
  }
}

object errors {

  import zio.http.Response
  import zio.http.Status

  case class ReturnUnifiedError               (message: String, status: Int = 500, code: Option[Int] = None, cause: Option[Throwable] = None) extends Exception(message, cause.orNull)
  case class ReturnResponseError              (response: Response)                                                         extends Exception
  case class ReturnResponseWithExceptionError (cause: Throwable, response: Response)                                       extends Exception(cause)

  object GuaraError {

    def of(response: Response)                                                   = ReturnResponseError(response)
    def of(response: Response)(ignored: Option[Nothing])                         = ReturnResponseError(response)
    def of(response: Response)(cause: Throwable)                                 = ReturnResponseWithExceptionError(cause, response)
    def of(message: String)                                                      = ReturnUnifiedError(message)
    def of(message: String)(ignored: Option[Nothing])                            = ReturnUnifiedError(message)
    def of(message: String)(cause: Throwable)                                    = ReturnUnifiedError(message, status = 500,         code = None      , cause = Some(cause))
    def of(code: Int, message: String)                                           = ReturnUnifiedError(message, status = 500,         code = Some(code), cause = None)
    def of(code: Int, message: String)(ignored: Option[Nothing])                 = ReturnUnifiedError(message, status = 500,         code = Some(code), cause = None)
    def of(code: Int, message: String)(cause: Throwable)                         = ReturnUnifiedError(message, status = 500,         code = Some(code), cause = Some(cause))
    def of(code: Int, status: Status, message: String)                           = ReturnUnifiedError(message, status = status.code, code = Some(code), cause = None)
    def of(code: Int, status: Status, message: String)(ignored: Option[Nothing]) = ReturnUnifiedError(message, status = status.code, code = Some(code), cause = None)
    def of(code: Int, status: Status, message: String)(cause: Throwable)         = ReturnUnifiedError(message, status = status.code, code = Some(code), cause = Some(cause))

    def fail[A](                  response: Response)                         : Task[A] = ZIO.fail(of(response))
    def fail[A](cause: Throwable, response: Response)                         : Task[A] = ZIO.fail(of(response)(cause))
    def fail[A](message: String)                                              : Task[A] = ZIO.fail(of(message))
    def fail[A](message: String, cause: Throwable)                            : Task[A] = ZIO.fail(of(message)(cause))
    def fail[A](code: Int, message: String)                                   : Task[A] = ZIO.fail(of(code, message))
    def fail[A](code: Int, status: Status, message: String)                   : Task[A] = ZIO.fail(of(code, status, message))
    def fail[A](code: Int, message: String, cause: Throwable)                 : Task[A] = ZIO.fail(of(code, message)(cause))
    def fail[A](code: Int, status: Status, message: String, cause: Throwable) : Task[A] = ZIO.fail(of(code, status, message)(cause))
  }
}

object domain {

  import zio.json.*
  import zio.http.URL
  import java.util.UUID
  import java.time.LocalDateTime
  import java.time.format.DateTimeFormatter
  import scala.concurrent.duration.Duration

  case class RequestId(id: String) {
    def track = ZIOAspect.annotated("rid", id)
  }

  object RequestId {
    def from(id: String)  : Task[RequestId]           = ZIO.succeed(RequestId(id))
    def decode(id: String): Either[String, RequestId] = Right(RequestId(id))
  }

  private val format = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

  given JsonDecoder[RequestId]              = JsonDecoder[String].mapOrFail(RequestId.decode)
  given JsonDecoder[Duration]               = JsonDecoder[String].map(Duration.apply)
  given JsonEncoder[Duration]               = JsonEncoder[String].contramap(_.toMillis + "ms")
  given JsonDecoder[LocalDateTime]          = JsonDecoder[String].map(ldt => LocalDateTime.parse(ldt, format))
}

object morbid {

  import config.*
  import domain.*
  import zio.http.*
  import zio.json.*
  import java.time.LocalDateTime

  trait Morbid {
    def start: UIO[Long]
  }

  case class Password(
    id       : Long,
    user     : Long,
    created  : LocalDateTime,
    deleted  : Option[LocalDateTime],
    method   : String,
    password : String,
    token    : String
  )

  case class User(
    id       : Long,
    account  : Account,
    created  : LocalDateTime,
    deleted  : Option[LocalDateTime],
    active   : Boolean,
    name     : String,
    email    : String,
    `type`   : String,
    password : Option[Password]
  )

  case class Account(
    id      : Long,
    active  : Boolean,
    created : LocalDateTime,
    deleted : Option[LocalDateTime],
    name    : String,
    `type`  : String
  )

  case class UserCache(users: Seq[User], etag: Option[String] = None)

  given JsonDecoder[Password] = DeriveJsonDecoder.gen[Password]
  given JsonDecoder[Account]  = DeriveJsonDecoder.gen[Account]
  given JsonDecoder[User]     = DeriveJsonDecoder.gen[User]

  case class RemoteMorbid(config: GuaraConfig, ref: Ref[UserCache], client: Client, scope: Scope) extends Morbid {

    private given Client = client
    private given Scope  = scope

    private val magic = Headers("X-Morbid-Magic" -> config.morbid.magic)

    private def update = {

      def parseAndUpdateCache(response: Response) = {
        //response.headers.foreach(println)
        val next = response.headers.get(Header.ETag.name)
        for {
          body     <- response.body.asString
          users    <- ZIO.fromEither(body.fromJson[Seq[User]]).mapError(new Exception(_))
          _        <- ref.update(_.copy(users = users, etag = next))
          _        <- ZIO.logInfo(s"User Cache Updated (etag: ${next.getOrElse("_")})")
        } yield users
      }

      def headers(cache: UserCache): Headers = {
        magic ++ cache
          .etag
          .map(value => Headers(Header.IfNoneMatch.name -> value))
          .getOrElse(Headers.empty)
      }

      for {
        cache    <- ref.get
        prev     =  cache.etag
        _        <- ZIO.logInfo(s"Updating User Cache (etag: ${prev.getOrElse("_")})")
        response <- utils.call(ZClient.request(Request.get(config.morbid.url).addHeaders(headers(cache))))
        users    <- ZIO.whenCase(response.status.code) {
          case 200  => parseAndUpdateCache(response)
          case 304  => ZIO.logInfo("User Cache Not Modified") *> ZIO.succeed(cache.users)
          case code => ZIO.fail(new Exception(s"Error Updating User Cache (status: ${code})"))
        }
      } yield users
    }

    override def start = {
      update
        .catchAll(e => ZIO.logError(s"Error Retrieving Users: ${e.getMessage}"))
        .repeat(Schedule.fixed(config.morbid.updateEvery))
    }
  }

  object Morbid {
    val layer = ZLayer.fromFunction(RemoteMorbid.apply)
  }
}

object id {

  import domain.RequestId
  import java.time.format.DateTimeFormatter
  import org.apache.commons.lang3.RandomStringUtils

  private val format = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

  def gen = {
    for {
      now    <- Clock.localDateTime
      suffix <- ZIO.succeed(RandomStringUtils.randomAlphanumeric(50))
      id     <- ZIO.attempt(now.format(format) + suffix) /* Keep it at 64 chars */
    } yield RequestId(id)
  }
}

object uef {

  import guara.utils.Origin
  import zio.http.{Header, Headers, Response, Status}
  import zio.json.*
  import org.apache.commons.lang3.exception.ExceptionUtils

  val HeaderName  = "X-ErrorType"
  val HeaderValue = "UEF"
  val UEFHeader   = Header.Custom(HeaderName, HeaderValue)

  case class UnifiedErrorFormat(
    origin  : Origin,
    message : String,
    status  : Int            = 500,
    code    : Option[Int]    = None,
    trace   : Option[String] = None
  )

  given JsonCodec[UnifiedErrorFormat] = DeriveJsonCodec.gen

  def traceGiven(cause: Option[Throwable], trace: Option[StackTrace]) = {
    (cause, trace) match
      case (_, Some(t)) if !t.isEmpty => Some(t.prettyPrint)
      case (Some(c), _)               => Some(ExceptionUtils.getStackTrace(c))
      case _                          => None
  }

  def toResponse(uef: UnifiedErrorFormat) = {
    val response = Response.json(uef.toJson)
    val headers  = Headers(Header.Custom("Error", uef.message), UEFHeader)
    response.copy(
      status = uef.code.map(Status.fromInt).getOrElse(Status.InternalServerError),
      headers = response.headers ++ headers
    )
  }

  def build(rue: ReturnUnifiedError, trace: StackTrace)(using origin: Origin) = {
    UnifiedErrorFormat(
      origin,
      message = rue.message,
      status  = rue.status,
      code    = rue.code,
      trace   = traceGiven(rue.cause, Some(trace))
    )
  }

  def build(cause: Throwable, maybe: Option[StackTrace] = None)(using origin: Origin): UnifiedErrorFormat = {
    UnifiedErrorFormat(
      origin,
      message = cause.getMessage,
      trace = traceGiven(Some(cause), maybe)
    )
  }

  def isUEFHeader(h: Header) = h.headerName == HeaderName && h.renderedValue == HeaderValue

}

object utils {

  import uef.*
  import errors.*
  import zio.http.*
  import zio.json.*
  import scala.util.matching.Regex
  import java.nio.charset.Charset

  opaque type Origin = String

  object Origin {
    def of(origin: String): Origin = origin
    given JsonCodec[Origin] = JsonCodec.string
  }

  val utf8 = Charset.forName("utf8")

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

  // w = [a-zA-Z_0-9]

  val code            = "[a-zA-Z0-9_]+".r
  val name            = "[\\w.\\- ]+".r
  val latinName       = "[À-ſ\\w.\\-&, ()'/]+".r
  val simpleLatinName = "[\\p{Latin}\\p{Common}]+".r

  def safeDecode(regex: Regex, maxLength: Int) = {
    JsonDecoder.string.mapOrFail { str =>
      (str.length > maxLength, regex.matches(str)) match
        case (true, _)  => Left(s"'$str' must have at most $maxLength chars")
        case (_, false) => Left(s"'$str' has invalid chars")
        case (_, true)  => Right(str.trim.replaceAll(" +", " "))
    }
  }

  def safeCode            = safeDecode(code           , _)
  def safeName            = safeDecode(name           , _)
  def safeLatinName       = safeDecode(latinName      , _)
  def safeSimpleLatinName = safeDecode(simpleLatinName, _)

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

  def ensureResponse(task: Task[Response])(using Origin): SafeResponse = {
    task
      .sandbox
      .catchAllTrace {
        case (Cause.Fail(ReturnResponseError(response)                , _), _    ) => ZIO.succeed(response)
        case (Cause.Fail(ReturnResponseWithExceptionError(_, response), _), _    ) => ZIO.succeed(response)
        case (Cause.Fail(rue : ReturnUnifiedError                     , _), trace) => ZIO.succeed(toResponse(build(rue, trace)))
        case (cause                                                       , trace) => ZIO.succeed(toResponse(build(cause.squash, Some(trace))))
      }
      .catchAllDefect { err => ZIO.succeed(toResponse(build(err))) }
  }

  extension (params: QueryParams)
    def get(name: String): Option[String] = params.getAll(name).headOption

  extension (url: URL)
    def queryParams(params: QueryParams): URL = url.updateQueryParams(_ => params)
}

object http {

  import config.GuaraConfig
  import zio.http.Server
  import zio.http.Server.RequestStreaming
  import guara.config.HttpSSLConfig
  import zio.http.*
  import zio.http.SSLConfig.HttpBehaviour
  import zio.json.*

  def urlFrom(raw: String) = ZIO.fromEither(URL.decode(raw)).mapError(err => Exception(s"Error parsing '$raw'"))

  object HttpServer {
    val layer = ZLayer.fromZIO {

      def toSSLConfig(ssl: HttpSSLConfig) = {
        SSLConfig.fromResource(
          behaviour               = HttpBehaviour.Redirect,
          certPath                = ssl.certificate,
          keyPath                 = ssl.key,
          clientAuth              = None,
          trustCertCollectionPath = None,
          includeClientCert       = false,
        )
      }

      for {
        cfg <- ZIO.service[GuaraConfig]
      } yield Server
        .defaultWith(
          _ .port(cfg.http.port)
            .requestStreaming(RequestStreaming.Disabled(cfg.http.maxRequestSize))
            .maxInitialLineLength(cfg.http.maxLineSize.getOrElse(4096))
            .maxHeaderSize(cfg.http.maxHeaderSize.getOrElse(8192))
            .copy(
              sslConfig = cfg.http.ssl.map(toSSLConfig)
            )
        )
    }.flatten
  }

  object client {

    object headers {
      val applicationJson = Header.ContentType(MediaType.application.json)
    }

    trait ResponseHandler[T] {
      def handle(response: Response): Task[T]
    }

//    trait BodyBuilder[T] {
//      def build(t: T): Task[Body]
//    }

    object handlers {

      val asText: ResponseHandler[String] = (res: Response) => res.body.asString

      def asTextIf(expected: Int): ResponseHandler[String] = (res: Response) => {
        for
          _    <- ZIO.when(res.status.code != expected) { ZIO.fail(Exception(s"Status code is ${res.status.code}. Expected $expected")) }
          text <- asText.handle(res)
        yield text
      }

      def asJson[T](using JsonDecoder[T]): ResponseHandler[T] = (res: Response) => {
        for
          text  <- res.body.asString
          value <- ZIO.fromEither(text.fromJson[T]).mapError(msg => Exception(s"Error decoding json: '$msg'"))
        yield value
      }

      def asJsonIf[T](expected: Int)(using JsonDecoder[T]): ResponseHandler[T] = (res: Response) => {
        for
          _    <- ZIO.when(res.status.code != expected) { ZIO.fail(Exception(s"Status code is ${res.status.code}. Expected $expected")) }
          value <- asJson.handle(res)
        yield value
      }
    }

    case class PinnedHttpClient(base: URL, fixed: Headers) {

      def execute[T](request: Request)(using handler: ResponseHandler[T]): ZIO[Client, Throwable, T] = {
        for
          res    <- Client.batched(request)//.provideLayer(ZLayer.succeed(client))
          result <- handler.handle(res)
        yield result
      }

      def get[T](url: URL, extra: Headers = Headers.empty)(using ResponseHandler[T]) = execute {
        Request.get(base ++ url).addHeaders(fixed.addHeaders(extra))
      }

      def post[T](url: URL, extra: Headers = Headers.empty)(body: Body)(using ResponseHandler[T]) = execute {
        Request.post(base ++ url, body).addHeaders(fixed.addHeaders(extra))
      }

      def postJson[T, B](url: URL, extra: Headers = Headers.empty)(body: B)(using ResponseHandler[T], JsonEncoder[B]) = execute {
        Request.post(base ++ url, Body.fromString(body.toJson)).addHeaders(fixed.addHeaders(extra).addHeader(headers.applicationJson))
      }
    }
  }
}

object kafka {

  import config.*
  import processor.*
  import zio.kafka.consumer.*
  import zio.kafka.serde.Serde
  import zio.kafka.serde.Deserializer
  import zio.kafka.serde.Serializer
  import zio.stream.ZStream
  import zio.stream.ZSink
  import org.apache.kafka.common.{Metric, MetricName, PartitionInfo, TopicPartition}
  import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
  import org.apache.kafka.clients.consumer.{ConsumerRecord, OffsetAndMetadata, OffsetAndTimestamp}

  trait KafkaConsumer {
    def start: UIO[AnyVal]
  }

  object KafkaConsumer {

    val layer = ZLayer.fromFunction(SimpleKafkaConsumer.apply)

    val consumer = ZLayer.scoped {

      def build(config: KafkaConfig): ZIO[Scope, Throwable, Consumer] = {

        def props = Map(
          "sasl.mechanism" -> "PLAIN",
          "security.protocol" -> "SASL_SSL",
          "ssl.endpoint.identification.algorithm" -> "https",
          "sasl.jaas.config" ->
            s"""
               |org.apache.kafka.common.security.plain.PlainLoginModule
               |required
               |username="${config.key}"
               |password="${config.secret}";
               |""".stripMargin.replaceAll("\n", " ")
        )

        def disabled: ZIO[Scope, Throwable, Consumer] = ZIO.succeed(new Consumer {
          override def assignment: Task[Set[TopicPartition]] = ???
          override def beginningOffsets(partitions: Set[TopicPartition], timeout: zio.Duration): Task[Map[TopicPartition, Long]] = ???
          override def endOffsets(partitions: Set[TopicPartition], timeout: zio.Duration): Task[Map[TopicPartition, Long]] = ???
          override def committed(partitions: Set[TopicPartition], timeout: zio.Duration): Task[Map[TopicPartition, Option[OffsetAndMetadata]]] = ???
          override def listTopics(timeout: zio.Duration): Task[Map[String, List[PartitionInfo]]] = ???
          override def partitionedAssignmentStream[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V]): stream.Stream[Throwable, Chunk[(TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])]] = ???
          override def partitionedStream[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V]): stream.Stream[Throwable, (TopicPartition, ZStream[R, Throwable, CommittableRecord[K, V]])] = ???
          override def stopConsumption: UIO[Unit] = ???
          override def consumeWith[R: zio.EnvironmentTag, R1: zio.EnvironmentTag, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V], commitRetryPolicy: Schedule[Any, Any, Any])(f: ConsumerRecord[K, V] => URIO[R1, Unit]): ZIO[R & R1, Throwable, Unit] = ???
          override def offsetsForTimes(timestamps: Map[TopicPartition, Long], timeout: zio.Duration): Task[Map[TopicPartition, OffsetAndTimestamp]] = ???
          override def partitionsFor(topic: String, timeout: zio.Duration): Task[List[PartitionInfo]] = ???
          override def position(partition: TopicPartition, timeout: zio.Duration): Task[Long] = ???
          override def subscription: Task[Set[String]] = ???
          override def metrics: Task[Map[MetricName, Metric]] = ???
          override def plainStream[R, K, V](subscription: Subscription, keyDeserializer: Deserializer[R, K], valueDeserializer: Deserializer[R, V], bufferSize: RuntimeFlags): ZStream[R, Throwable, CommittableRecord[K, V]] = ZStream.empty
        })

        if (config.consumer.enabled) {
          for {
            consumer <- Consumer.make(ConsumerSettings(config.servers.toList).withGroupId(config.consumer.group).withProperties(props))
          } yield consumer
        } else disabled
      }

      for {
        config   <- ZIO.service[GuaraConfig]
        _        <- ZIO.logInfo(s"Kafka Settings: enabled=${config.kafka.consumer.enabled}, key=${config.kafka.key}, topic=${config.kafka.consumer.topic}, servers=${config.kafka.servers.mkString(",")}")
        consumer <- build(config.kafka)
        _        <- ZIO.logInfo("Kafka Consumer Configured")
      } yield consumer
    }
  }

  case class SimpleKafkaConsumer(config: GuaraConfig, consumer: Consumer, processor: Processor) extends KafkaConsumer {

    private def startConsumer: Task[Long] = {
      consumer
        .plainStream(
          subscription      = Subscription.topics(config.kafka.consumer.topic),
          keyDeserializer   = Serde.string,
          valueDeserializer = Serde.string)
        .tap(processor.process)
        .map(_.offset)
        .aggregateAsync(Consumer.offsetBatches)
        .mapZIO(_.commitOrRetry(Schedule.spaced(1.minute) && Schedule.recurs(10)))
        .run(ZSink.timed)
        .map(_.toSeconds)
    }

    override def start = {
      for {
        _      <- ZIO.logInfo(s"Starting Kafka Consumer")
        result <- startConsumer.catchAll(cause => ZIO.logErrorCause("Error Starting Kafka Consumer", Cause.fail(cause)))
        _      <- ZIO.logInfo(s"Kafka Consumer Started")
      } yield result
    }
  }
}

object background {

  import kafka.KafkaConsumer

  trait BackgroundServices {
    def start: UIO[Unit]
  }

  case class NoBackgroundServices() extends BackgroundServices {
    override def start = ZIO.unit
  }

  case class KafkaClient(kafka: KafkaConsumer) extends BackgroundServices {
    override def start =
      for
        _  <- ZIO.logInfo(s"Starting Kafka Consumer")
        _  <- kafka.start.forkDaemon
      yield ()
  }

  object BackgroundServices {
    val none  = ZLayer.succeed(NoBackgroundServices())
    val kafka = ZLayer.succeed(NoBackgroundServices())
  }
}

object processor {

  import zio.kafka.consumer.CommittableRecord

  trait Processor {
    def process(record: CommittableRecord[String, String]): UIO[Unit]
  }

  object Processor {
    val drop = ZLayer.fromFunction(() => DropProcessor())
  }

  case class DropProcessor() extends Processor {
    override def process(record: CommittableRecord[String, String]): UIO[Unit] = ZIO.unit
  }
}

object router {

  import config.*
  import domain.*
  import morbid.Morbid

  import better.files.*
  import zio.json.*
  import zio.http.*
  import zio.json.ast.*
  import zio.json.ast.JsonCursor.*
  import zio.http.codec.PathCodec.{string, empty, trailing, uuid}
  import zio.http.ChannelEvent.{ExceptionCaught, Read, UserEvent, UserEventTriggered}
  import zio.http.Header.{AccessControlAllowMethods, AccessControlAllowOrigin, Origin}
  import zio.http.Middleware.{CorsConfig, cors}
  import zio.http.codec.{PathCodec, SegmentCodec}
  import java.util.UUID
  import java.io.InputStream
  import java.nio.charset.StandardCharsets
  import scala.util.Try

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
}

trait GuaraApp extends ZIOAppDefault {

  import config.GuaraConfig
  import http.HttpServer
  import router.Router
  import background.BackgroundServices
  import zio.http.Server

  private val srv   : ZLayer[GuaraConfig, Throwable, Server]               = HttpServer.layer
  private val basic : ZLayer[Any        , Throwable, GuaraConfig & Server] = GuaraConfig.layer >>> (srv ++ GuaraConfig.layer)

  def services: ZIO[BackgroundServices & Router & Server & GuaraConfig, Throwable, Unit] =
    for
      config <- ZIO.service[GuaraConfig]
      router <- ZIO.service[Router]
      bg     <- ZIO.service[BackgroundServices]
      _      <- ZIO.logInfo(s"Starting Background Services")
      _      <- bg.start.forkDaemon
      _      <- ZIO.logInfo(s"Starting HTTP Server (${config.name} at ${config.http.port})")
      _      <- Server.serve(router.routes)
    yield ()

  def startGuara         : ZIO[BackgroundServices & Router              , Throwable, Unit] = services.provideSomeLayer(basic)
  def startGuaraNoConfig : ZIO[BackgroundServices & Router & GuaraConfig, Throwable, Unit] = services.provideSomeLayer(srv)
}