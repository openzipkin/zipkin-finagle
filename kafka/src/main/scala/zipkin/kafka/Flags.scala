// intentionally in package zipkin.kafka, not zipkin.finagle.kafka as this
// translates directly to a property name which is only relevant in finagle.
// Ex. -zipkin.kafka.topic=zipkin-2 vs -zipkin.finagle.kafka.topic=zipkin-2
package zipkin.kafka

import java.net.InetSocketAddress

import com.twitter.app.GlobalFlag

object bootstrapServers extends GlobalFlag[Seq[InetSocketAddress]](
  List(new InetSocketAddress("localhost", 9092)),
  "Initial set of kafka servers to connect to, rest of cluster will be discovered (comma separated)")

object topic extends GlobalFlag[String](
  "zipkin",
  "Kafka topic zipkin traces will be sent to")
