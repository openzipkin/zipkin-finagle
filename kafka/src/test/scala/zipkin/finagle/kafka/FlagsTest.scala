package zipkin.finagle.kafka

import java.net.InetSocketAddress

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import zipkin.kafka.{bootstrapServers => bootstrapServersFlag, topic => topicFlag}
import zipkin.{initialSampleRate => sampleRateFlag}

@RunWith(classOf[JUnitRunner])
class FlagsTest extends FunSuite {

  test("flag names aren't redundant or long on accident of java packages") {
    assert(sampleRateFlag.name === "zipkin.initialSampleRate")
    assert(bootstrapServersFlag.name === "zipkin.kafka.bootstrapServers")
    assert(topicFlag.name === "zipkin.kafka.topic")
  }

  test("bootstrapServers parses multiple hosts") {
    bootstrapServersFlag.parse("host1:9092,host2:9092")

    assert(bootstrapServersFlag() ===
      Seq(new InetSocketAddress("host1", 9092), new InetSocketAddress("host2", 9092)))
  }
}
