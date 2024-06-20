package guara

import zio.*
import zio.http.Server.RequestStreaming

object config {

  import zio.config.*
  import zio.config.magnolia.*
  import zio.config.typesafe.*
  import Config.*

  import zio.http.Header
  import zio.http.Header.{AccessControlAllowOrigin, AccessControlAllowMethods, Origin}
  import zio.http.Middleware.CorsConfig

  case class KafkaConsumerConfig(enabled: Boolean, group: String, topic: String)
  case class KafkaConfig(key: String, secret: String, servers: Seq[String], consumer: KafkaConsumerConfig)
  case class HttpConfig(port: Int, maxRequestSize: Int)
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
  }
}

object errors {

  import zio.http.Response
  import zio.http.Status

  case class ReturnResponseError              (response: Response)                   extends Exception
  case class ReturnResponseWithExceptionError (cause: Throwable, response: Response) extends Exception

  object GuaraError {

    def of(response: Response)                   = ReturnResponseError(response)
    def of(response: Response)(cause: Throwable) = ReturnResponseWithExceptionError(cause, response)

    def fail[A](                  response: Response) : Task[A] = ZIO.fail(of(response))
    def fail[A](cause: Throwable, response: Response) : Task[A] = ZIO.fail(of(response)(cause))
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
    val layer = ZLayer.fromFunction(RemoteMorbid.apply _)
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

object utils {

  import errors.*
  import zio.http.*
  import zio.json.*
  import scala.util.matching.Regex
  import java.nio.charset.Charset

  val utf8 = Charset.forName("utf8")

  extension (body: zio.http.Body) {

    def parse[T](using jsonDecoder: JsonDecoder[T], charset: Charset = utf8): Task[T] = {
      for {
        str   <- body.asString(charset)
        value <- str.fromJson[T] match {
                   case Right(value) => ZIO.succeed(value)
                   case Left(err)    => ZIO.fail(new Exception(s"Failure parsing json body: $err"))
                 }
      } yield value
    }
  }

  // w = [a-zA-Z_0-9]

  val code      = "[a-zA-Z0-9_]+".r
  val name      = "[\\w\\.\\- ]+".r
  val latinName = "[\u00C0-\u017F\\w\\.\\- ]+".r

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

  extension (string: String)
    def as[T]: T = string.asInstanceOf[T]

  extension (long: Long)
    def as[T]: T = long.asInstanceOf[T]

  def call(z: ZIO[Client & Scope, Throwable, Response])(using client: Client, scope: Scope): Task[Response] = {
    z.provide(ZLayer.succeed(client), ZLayer.succeed(scope))
  }

  def ensureResponse(task: Task[Response]): Task[Response] = {
    task.catchAllTrace {
      case (ReturnResponseError(response)                  , _    ) => ZIO.succeed(response)
      case (ReturnResponseWithExceptionError(err, response), trace) => ZIO.logErrorCause("Failure", Cause.fail(err, trace)) *> ZIO.succeed(response)
      case (err                                            , trace) => ZIO.logErrorCause("Failure", Cause.fail(err, trace)) *> ZIO.succeed(Response.internalServerError)
    }.catchAllDefect {
      case err                                                      => ZIO.logErrorCause("Defect", Cause.fail(err)) *> ZIO.succeed(Response.internalServerError)
    } //.catchNonFatalOrDie
  }

  //    def trap(task: ZIO[Any, Throwable, Response]): Task[Response] = {
  //      task.catchAllCause {
  //        case it@Cause.Empty => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Die(throwable, trace) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Interrupt(fiberId, trace) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Stackless(cause, stackless) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Then(left, right) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Both(left, right) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //        case it@Cause.Fail(pe: GuaraError, trace) => ZIO.logErrorCause(pe.asErrorMessage, it) *> pe.asResponse
  //        case it@Cause.Fail(ex, trace) => ZIO.logErrorCause("", it) *> ZIO.succeed(Response.internalServerError("TODO"))
  //      }
  //    }
}

object http {

  import config.GuaraConfig
  import zio.http.Server

  object HttpServer {
    val layer = ZLayer.fromZIO {
      for {
        cfg <- ZIO.service[GuaraConfig]
      } yield Server.defaultWith(_.port(cfg.http.port).requestStreaming(RequestStreaming.Disabled(cfg.http.maxRequestSize)))
    }.flatten
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

    val layer = ZLayer.fromFunction(SimpleKafkaConsumer.apply _)

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

object processor {

  import domain.*
  import config.*
  import zio.kafka.consumer.CommittableRecord
  import java.time.{LocalDateTime, ZonedDateTime}

  trait Processor {
    def process(record: CommittableRecord[String, String]): UIO[Unit]
  }

  object Processor {
    val drop = ZLayer.fromFunction(DropProcessor.apply _)
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
    def routes: HttpApp[Any]
  }

  object Echo {

    def routes: HttpApp[Any] = {

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
      ).sandbox.toHttpApp
    }
  }
}

trait GuaraApp extends ZIOAppDefault {

  import processor.Processor
  import zio.http.Client
  import zio.kafka.consumer.Consumer
  import morbid.UserCache
  import config.GuaraConfig
  import http.HttpServer
  import kafka.KafkaConsumer
  import router.Router
  import morbid.Morbid
  import zio.http.Server

  private val cch   : ZLayer[Any                            , Throwable, Ref[UserCache]]                                = ZLayer(Ref.make(UserCache(Seq.empty)))
  private val srv   : ZLayer[GuaraConfig                    , Throwable, Server]                                        = HttpServer.layer
  private val mor   : ZLayer[Scope & GuaraConfig            , Throwable, Morbid]                                        = (cch ++ Client.default) >>> Morbid.layer
  private val kaf   : ZLayer[Scope & GuaraConfig & Processor, Throwable, KafkaConsumer]                                 = KafkaConsumer.consumer >>> KafkaConsumer.layer
  private val basic : ZLayer[Processor                      , Throwable, GuaraConfig & Morbid & Server & KafkaConsumer] = (GuaraConfig.layer ++ Scope.default) >>> (mor ++ srv ++ kaf ++ GuaraConfig.layer)

  private def services: ZIO[Router & Server & Morbid & KafkaConsumer & GuaraConfig, Throwable, Unit] = for {
    config <- ZIO.service[GuaraConfig]
    kafka  <- ZIO.service[KafkaConsumer]
    router <- ZIO.service[Router]
    morbid <- ZIO.service[Morbid]
    _      <- ZIO.logInfo(s"Starting ${config.name} at '${config.http.port}'")
    _      <- kafka.start.forkDaemon
    //_      <- morbid.start.forkDaemon
    _      <- Server.serve(router.routes)
  } yield ()

  def startGuara: ZIO[Processor & Router, Throwable, Unit] = services.provideSomeLayer(basic)
}