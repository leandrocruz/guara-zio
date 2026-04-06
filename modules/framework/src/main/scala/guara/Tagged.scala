package guara

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.sift.AbstractDiscriminator
import scala.jdk.CollectionConverters.*

class Tagged extends AbstractDiscriminator[ILoggingEvent] {
  override def getDiscriminatingValue(e: ILoggingEvent): String = e.getKeyValuePairs.asScala.find(_.key == "tag").map(_.value.toString).getOrElse("no-tag")
  override def getKey: String = "tag"
}
