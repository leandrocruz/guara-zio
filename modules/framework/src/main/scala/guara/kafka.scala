package guara.framework.kafka

import zio.*

import guara.framework.config.*
import guara.framework.processor.*
import org.apache.kafka.clients.consumer.{ConsumerRecord, OffsetAndMetadata, OffsetAndTimestamp}
import org.apache.kafka.common.{Metric, MetricName, PartitionInfo, TopicPartition}
import zio.kafka.consumer.*
import zio.kafka.serde.{Deserializer, Serde}
import zio.stream.{ZSink, ZStream}

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
