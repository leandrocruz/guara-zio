package guara.framework.processor

import zio.*

import guara.framework.config.GuaraConfig
import zio.kafka.consumer.CommittableRecord

trait Processor {
  def process(record: CommittableRecord[String, String]): UIO[Unit]
}

object Processor {
  val drop = ZLayer.fromFunction(DropProcessor.apply)
}

case class DropProcessor(config: GuaraConfig) extends Processor {
  override def process(record: CommittableRecord[String, String]): UIO[Unit] = ZIO.unit
}