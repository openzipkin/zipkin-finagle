package zipkin.finagle.http

import com.twitter.conversions.storage._
import com.twitter.finagle.http.{Method, Request}
import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.thrift.Protocols
import com.twitter.finagle.tracing.{Trace, NullTracer}
import com.twitter.finagle.util.DefaultTimer
import com.twitter.finagle.zipkin.core.{InternalZipkinTracer, RawZipkinTracer, Span}
import com.twitter.finagle.{Http, param}
import com.twitter.util._
import org.apache.thrift.protocol.{TList, TType}
import org.apache.thrift.transport.TMemoryBuffer


/**
 * Receives the Finagle generated traces and sends them off to Zipkin via Http.
 *
 * @param host the zipkin host (also used as the http host header).
 * @param statsReceiver see [[RawZipkinTracer.statsReceiver]]
 * @param timer see [[RawZipkinTracer.timer]]
 */
private[http] class HttpRawZipkinTracer(
  host: String, // Finagle doesn't automatically manage host headers; this is used later
  statsReceiver: StatsReceiver,
  timer: Timer = DefaultTimer.twitter,
  initialBufferSize: StorageUnit = 512.bytes
) extends InternalZipkinTracer(statsReceiver, timer) {
  private[this] val client = Http.client.configured(param.Tracer(NullTracer)).newClient(host).toService
  private[this] val okCounter = statsReceiver.scope("log_span").counter("ok")
  private[this] val errorReceiver = statsReceiver.scope("log_span").scope("error")

  private[this] val protocolFactory = Protocols.binaryFactory(statsReceiver = statsReceiver)
  private[this] val initialSizeInBytes = initialBufferSize.inBytes.toInt

  /** Logs spans via POST /api/v1/spans. */
  override def sendSpans(spans: Seq[Span]): Future[Unit] = {
    val serializedSpans = spansToThriftByteArray(spans)
    val request = Request(Method.Post, "/api/v1/spans")
    request.headerMap.add("Host", host)
    request.headerMap.add("Content-Type", "application/x-thrift")
    request.headerMap.add("Content-Length", serializedSpans.length.toString)
    request.write(serializedSpans)

    Trace.letClear { // Don't recurse by tracing our POST to zipkin
      client(request)
        .onSuccess(_ => okCounter.incr())
        .rescue {
          case NonFatal(e) => errorReceiver.counter(e.getClass.getName).incr(); Future.Done
        }.unit
    }
  }

  private[this] def spansToThriftByteArray(spans: Seq[Span]): Array[Byte] = {
    // serialize all spans as a thrift list
    val transport = new TMemoryBuffer(initialSizeInBytes) // bytes
    val protocol = protocolFactory.getProtocol(transport)
    protocol.writeListBegin(new TList(TType.STRUCT, spans.size))
    spans.foreach(_.toThrift.write(protocol))
    protocol.writeListEnd()
    transport.getArray
  }
}
