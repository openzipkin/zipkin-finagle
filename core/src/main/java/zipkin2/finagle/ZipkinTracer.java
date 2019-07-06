/*
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin2.finagle;

import com.twitter.finagle.stats.DefaultStatsReceiver$;
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
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

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
 *
 * <p>If you don't need to use service loader, an alternate way to manually configure the tracer is
 * via {@link #newBuilder(Sender)}
 */
// It would be cleaner to obviate SamplingTracer and the dependency on finagle-zipkin-core, but
// SamplingTracer includes unrelated event logic https://github.com/twitter/finagle/issues/540
public class ZipkinTracer extends SamplingTracer implements Closable {

  /**
   * Normally, Finagle configures the tracer implicitly and through flags. This implies constraints
   * needed for service loading. Alternatively, you can use this type to explicitly configure any
   * sender available in Zipkin.
   *
   * <p>Ex.
   * <pre>{@code
   * // Setup a sender without using Finagle flags like so:
   * Properties overrides = new Properties();
   * overrides.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
   * sender = KafkaSender.newBuilder()
   *   .bootstrapServers("host1:9092,host2:9092")
   *   .overrides(overrides)
   *   .encoding(Encoding.PROTO3)
   *   .build();
   *
   * // Now, use it here, but don't forget to close the sender!
   * tracer = ZipkinTracer.newBuilder(sender).build();
   * }</pre>
   *
   * <p><em>Note</em>: You must close the supplied sender externally, after this instance is
   * closed.
   */
  public static Builder newBuilder(Sender sender) {
    return new Builder(sender);
  }

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

    @Override public Option<Object> sampleTrace(TraceId traceId) {
      return Some.apply(true);
    }

    @Override public boolean isNull() {
      return false;
    }

    @Override public boolean isActivelyTracing(TraceId traceId) {
      return true;
    }

    /**
     * Mutates the Span with whatever new info we have. If we see an "end" annotation we remove the
     * span and send it off.
     */
    @Override public void record(Record record) {
      recorder.record(record);
    }
  }

  public static final class Builder {
    final Sender sender;
    StaticConfig config = new StaticConfig();
    StatsReceiver stats = DefaultStatsReceiver$.MODULE$.get().scope("zipkin");

    Builder(Sender sender) {
      if (sender == null) throw new NullPointerException("sender == null");
      this.sender = sender;
    }

    /**
     * Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0].
     *
     * <p>Default is the value of the flag {@code zipkin.initialSampleRate} which if not overridden
     * is 0.001f (0.1%).
     */
    public Builder initialSampleRate(float initialSampleRate) {
      if (initialSampleRate < 0.0f || initialSampleRate > 1.0f) {
        throw new IllegalArgumentException("initialSampleRate must be in the range [0.0 - 1.0]");
      }
      this.config.initialSampleRate = initialSampleRate;
      return this;
    }

    /** Name of the scope to emit span metrics to. Default "zipkin" */
    public Builder statsScope(String statsScope) {
      if (statsScope == null) throw new NullPointerException("statsScope == null");
      this.stats = DefaultStatsReceiver$.MODULE$.get().scope(statsScope);
      return this;
    }

    public ZipkinTracer build() {
      return new ZipkinTracer(sender, config, stats);
    }
  }

  static final class StaticConfig implements Config {
    float initialSampleRate = zipkin.initialSampleRate$.Flag.apply();

    @Override public float initialSampleRate() {
      return initialSampleRate;
    }
  }
}
