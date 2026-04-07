package guara.framework.background

import zio.*

import guara.framework.kafka.KafkaConsumer

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