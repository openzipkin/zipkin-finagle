package zipkin.finagle.http

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import zipkin.http.{host => hostFlag}
import zipkin.{initialSampleRate => sampleRateFlag}

@RunWith(classOf[JUnitRunner])
class FlagsTest extends FunSuite {

  test("flag names aren't redundant or long on accident of java packages") {
    assert(sampleRateFlag.name === "zipkin.initialSampleRate")
    assert(hostFlag.name === "zipkin.http.host")
  }
}
