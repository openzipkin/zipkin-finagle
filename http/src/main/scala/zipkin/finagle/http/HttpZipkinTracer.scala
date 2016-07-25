package zipkin.finagle.http

import com.twitter.finagle.stats.{DefaultStatsReceiver, NullStatsReceiver, StatsReceiver}
import com.twitter.finagle.tracing.Tracer
import com.twitter.finagle.zipkin.core.SamplingTracer
import zipkin.http.{host => hostFlag}
import zipkin.{initialSampleRate => sampleRateFlag}

object HttpZipkinTracer {
  lazy val default: Tracer = mk()

  /**
   * @param host the zipkin host (also used as the http host header)
   * @param statsReceiver Where to log information about tracing success/failures
   * @param sampleRate How much data to collect. Default sample rate 0.1%. Max is 1, min 0.
   */
  def mk(
    host: String = hostFlag(),
    statsReceiver: StatsReceiver = NullStatsReceiver,
    sampleRate: Float = sampleRateFlag()
  ): HttpZipkinTracer =
    new HttpZipkinTracer(new HttpRawZipkinTracer(host, statsReceiver), sampleRate)

  /**
   * Util method since named parameters can't be called from Java
    *
    * @param statsReceiver Where to log information about tracing success/failures
   */
  def mk(statsReceiver: StatsReceiver): Tracer =
    mk(host = hostFlag(), statsReceiver = statsReceiver, sampleRate = sampleRateFlag())
}

class HttpZipkinTracer(
  underlying: HttpRawZipkinTracer,
  sampleRate: Float
) extends SamplingTracer(underlying, sampleRate) {
  /** Default constructor for the service loader */
  def this() = this(
    new HttpRawZipkinTracer(
      host = hostFlag(),
      statsReceiver = DefaultStatsReceiver.scope("zipkin.http")
    ), sampleRateFlag())
}
