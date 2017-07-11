/**
 * Copyright 2016-2017 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin.finagle;

import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.finagle.util.DefaultTimer;
import com.twitter.finagle.zipkin.core.SamplingTracer;
import com.twitter.util.Closable;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.Time;
import scala.Option;
import scala.Some;
import scala.runtime.BoxedUnit;
import scala.runtime.BoxesRunTime;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Sender;

/**
 * Receives the Finagle generated traces and sends them off to Zipkin.
 *
 * <p>Implement this by extending and registering in the service loader. For example:
 * <pre>
 * &#064;AutoService(com.twitter.finagle.tracing.Tracer.class)
 * public final class HttpZipkinTracer extends ZipkinTracer {
 *
 *  --snip--
 *
 *   // Default constructor for the service loader
 *   public HttpZipkinTracer() {
 *     this(Config.builder().build(),
 *          DefaultStatsReceiver$.MODULE$.get().scope("zipkin.http")
 *     );
 *   }
 *
 *   HttpZipkinTracer(Config config, StatsReceiver stats) {
 *     super(new HttpReporter(config.host()), stats, config.initialSampleRate());
 *   }
 * }</pre>
 */
// It would be cleaner to obviate SamplingTracer and the dependency on finagle-zipkin-core, but
// SamplingTracer includes unrelated event logic https://github.com/twitter/finagle/issues/540
public class ZipkinTracer extends SamplingTracer implements Closable {
  final AsyncReporter<Span> reporter;
  final RawZipkinTracer underlying;

  protected ZipkinTracer(Sender sender, Config config, StatsReceiver stats) {
    this(AsyncReporter.builder(sender)
        .metrics(new ReporterMetricsAdapter(stats))
        .build(), config, stats);
  }

  ZipkinTracer(AsyncReporter<Span> reporter, Config config, StatsReceiver stats) {
    this(reporter, new RawZipkinTracer(reporter, stats), config);
  }

  private ZipkinTracer(AsyncReporter<Span> reporter, RawZipkinTracer underlying, Config config) {
    super(underlying, config.initialSampleRate());
    this.reporter = reporter;
    this.underlying = underlying;
  }

  @Override public Future<BoxedUnit> close() {
    return close(Time.Bottom());
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    reporter.close();
    return underlying.recorder.close(deadline);
  }

  @Override public Future<BoxedUnit> close(Duration after) {
    return close(after.fromNow());
  }

  protected interface Config {
    /** How much data to collect. Default sample rate 0.001 (0.1%). Max is 1, min 0. */
    float initialSampleRate();
  }

  static final class RawZipkinTracer implements Tracer {
    private final SpanRecorder recorder;

    /**
     * @param stats We generate stats to keep track of traces sent, failures and so on
     */
    RawZipkinTracer(AsyncReporter<Span> reporter, StatsReceiver stats) {
      this.recorder = new SpanRecorder(reporter, stats, DefaultTimer.getInstance());
    }

    @Override
    public Option<Object> sampleTrace(TraceId traceId) {
      return Some.apply(true);
    }

    @Override
    public boolean isNull() {
      return false;
    }

    @Override
    public boolean isActivelyTracing(TraceId traceId) {
      return true;
    }

    /**
     * Mutates the Span with whatever new info we have. If we see an "end" annotation we remove the
     * span and send it off.
     */
    @Override
    public void record(Record record) {
      recorder.record(record);
    }
  }
}
