package zipkin.finagle.http

import java.net.URI
import java.util
import java.util.Arrays.asList

import com.twitter.finagle.ChannelClosedException
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.tracing.{Flags, SpanId, TraceId}
import com.twitter.finagle.zipkin.core.{BinaryAnnotation, Endpoint, Span, ZipkinAnnotation}
import com.twitter.util.{Await, Base64StringEncoder, Time}
import org.junit.{ClassRule, Test}
import org.scalatest.junit.JUnitSuite
import zipkin.Codec
import zipkin.junit.{HttpFailure, ZipkinRule}

object HttpRawZipkinTracerTest {
  // Singleton as the test needs to read the actual port in use
  val zipkinRule = new ZipkinRule()

  // Scala cannot generate fields with public visibility, so use a def instead.
  @ClassRule def zipkinDef = zipkinRule
}

class HttpRawZipkinTracerTest extends JUnitSuite {

  import HttpRawZipkinTracerTest.zipkinRule

  val statsReceiver = new InMemoryStatsReceiver

  val traceId = TraceId(Some(SpanId(123)), Some(SpanId(123)), SpanId(123), None, Flags().setDebug)

  @Test def sendSpans {
    val localEndpoint = Endpoint(2323, 23)
    val remoteEndpoint = Endpoint(333, 22)

    val annotations = Seq(
      ZipkinAnnotation(Time.fromSeconds(123), "cs", localEndpoint),
      ZipkinAnnotation(Time.fromSeconds(126), "cr", localEndpoint),
      ZipkinAnnotation(Time.fromSeconds(123), "ss", remoteEndpoint),
      ZipkinAnnotation(Time.fromSeconds(124), "sr", remoteEndpoint),
      ZipkinAnnotation(Time.fromSeconds(123), "llamas", localEndpoint)
    )

    val span = Span(
      traceId = traceId,
      annotations = annotations,
      _serviceName = Some("hickupquail"),
      _name = Some("foo"),
      bAnnotations = Seq.empty[BinaryAnnotation],
      endpoint = localEndpoint)

    val expected = Base64StringEncoder.decode(
      """DAAAAAEKAAEAAAAAAAAAewsAAwAAAANmb28KAAQAAAAAAAAAewoABQAAAAAAAAB7DwAGDAA
        |AAAUKAAEAAAAAB1TUwAsAAgAAAAJjcwwAAwgAAQAACRMGAAIAFwsAAwAAAAtoaWNrdXBxdW
        |FpbAAACgABAAAAAAeCm4ALAAIAAAACY3IMAAMIAAEAAAkTBgACABcLAAMAAAALaGlja3Vwc
        |XVhaWwAAAoAAQAAAAAHVNTACwACAAAAAnNzDAADCAABAAABTQYAAgAWCwADAAAAC2hpY2t1
        |cHF1YWlsAAAKAAEAAAAAB2QXAAsAAgAAAAJzcgwAAwgAAQAAAU0GAAIAFgsAAwAAAAtoaWN
        |rdXBxdWFpbAAACgABAAAAAAdU1MALAAIAAAAGbGxhbWFzDAADCAABAAAJEwYAAgAXCwADAA
        |AAC2hpY2t1cHF1YWlsAAACAAkBAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        |AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        |AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        |AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=""".stripMargin)

    Await.result(tracer().sendSpans(Seq(span)))

    assert(withoutTimestampAndDuration(zipkinRule.getTraces())
      == asList(Codec.THRIFT.readSpans(expected)))
    assert(statsReceiver.counters == Map(List("log_span", "ok") -> 1))
  }

  @Test def sendSpans_incrementsErrorCounterOnFailure {
    zipkinRule.enqueueFailure(HttpFailure.disconnectDuringBody())

    val span = Span(
      traceId = traceId,
      annotations = Seq.empty,
      _serviceName = Some("hickupquail"),
      _name = Some("foo"),
      bAnnotations = Seq.empty[BinaryAnnotation],
      endpoint = Endpoint(2323, 23))

    Await.result(tracer().sendSpans(Seq(span)))

    assert(statsReceiver.counters ==
      Map(List("log_span", "error",
        classOf[ChannelClosedException].getName()) -> 1))
  }

  def tracer(): HttpRawZipkinTracer = {
    val httpUrl = URI.create(zipkinRule.httpUrl())

    new HttpRawZipkinTracer(httpUrl.getHost + ":" + httpUrl.getPort, statsReceiver)
  }

  /** Finagle doesn't report the span's timestamp and duration, yet, as it hasn't updated thrifts */
  def withoutTimestampAndDuration(traces: util.List[util.List[zipkin.Span]]) = {
    import collection.JavaConverters._
    traces.asScala.map(spans => spans.asScala.map(s =>
      s.toBuilder().timestamp(null).duration(null).build()).asJava).asJava
  }
}
