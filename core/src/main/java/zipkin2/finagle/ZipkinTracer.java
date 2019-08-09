/*
 * Copyright 2016-2019 The OpenZipkin Authors
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
import com.twitter.finagle.tracing.Annotation;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.finagle.util.DefaultTimer;
import com.twitter.finagle.zipkin.core.SamplingTracer;
import com.twitter.util.Closable;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.Time;
import java.io.Closeable;
import java.io.IOException;
import scala.Option;
import scala.Some;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin2.Span;
import zipkin2.internal.Nullable;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Reporter;
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
 * via {@link #newBuilder(Reporter)}
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
   * zipkinStats = stats.scope("zipkin");
   * spanReporter = AsyncReporter.builder(sender)
   *   .metrics(new ReporterMetricsAdapter(zipkinStats)) // routes reporter metrics to finagle stats
   *   .build()
   *
   * // Now, use it here, but don't forget to close the sender!
   * tracer = ZipkinTracer.newBuilder(spanReporter).stats(zipkinStats).build();
   * }</pre>
   *
   * <h3>On closing resources</h3>
   * The resulting tracer will attempt to close an underlying reporter if it implements {@link
   * Closeable}. It is best to use normal tools like pre-destroy hooks to close resources in your
   * application. If you somehow cannot control your resources, yet can invoke this, consider
   * wrapping the input as a closeable to coordinate an ordered shutdown.
   *
   * <p>Ex.
   * <pre>{@code
   * class ReporterThatClosesSender implements Reporter<Span>, Closeable {
   *   final Sender sender;
   *   final AsyncReporter<Span> reporter;
   *
   *   @Override public void close() throws IOException {
   *     reporter.close();
   *     sender.close();
   *   }
   *
   *   @Override public void report(Span span) {
   *     reporter.report(span);
   *   }
   * }
   * }</pre>
   */
  public static Builder newBuilder(Reporter<Span> spanReporter) {
    return new Builder(spanReporter);
  }

  final Reporter<Span> reporter;
  final RawZipkinTracer underlying;

  protected ZipkinTracer(Sender sender, Config config, StatsReceiver stats) {
    this(AsyncReporter.builder(sender)
        .metrics(new ReporterMetricsAdapter(stats))
        .build(), config, stats);
  }

  ZipkinTracer(Reporter<Span> reporter, Config config, StatsReceiver stats) {
    this(reporter, new RawZipkinTracer(reporter, stats, config.localServiceName()), config);
  }

  private ZipkinTracer(Reporter<Span> reporter, RawZipkinTracer underlying, Config config) {
    super(underlying, config.initialSampleRate());
    this.reporter = reporter;
    this.underlying = underlying;
  }

  @Override public Future<BoxedUnit> close() {
    return close(Time.Bottom());
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    Future<BoxedUnit> result = underlying.recorder.close(deadline);
    if (!(reporter instanceof Closeable)) return result;
    return result.onSuccess(new AbstractFunction1<BoxedUnit, BoxedUnit>() {
      @Override public BoxedUnit apply(BoxedUnit v1) {
        try {
          ((Closeable) reporter).close();
        } catch (IOException | RuntimeException ignored) {
        }
        return BoxedUnit.UNIT;
      }
    });
  }

  @Override public Future<BoxedUnit> close(Duration after) {
    return close(after.fromNow());
  }

  protected interface Config {
    @Nullable String localServiceName();

    /** How much data to collect. Default sample rate 0.001 (0.1%). Max is 1, min 0. */
    float initialSampleRate();
  }

  static final class RawZipkinTracer implements Tracer {
    private final SpanRecorder recorder;

    /**
     * @param stats We generate stats to keep track of traces sent, failures and so on
     */
    RawZipkinTracer(Reporter<Span> reporter, StatsReceiver stats, String localServiceName) {
      this.recorder =
          new SpanRecorder(reporter, stats, DefaultTimer.getInstance(), localServiceName);
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
    final Reporter<Span> spanReporter;
    StaticConfig config = new StaticConfig();
    StatsReceiver stats = DefaultStatsReceiver$.MODULE$.get().scope("zipkin");

    Builder(Reporter<Span> spanReporter) {
      if (spanReporter == null) throw new NullPointerException("spanReporter == null");
      this.spanReporter = spanReporter;
    }

    /**
     * Lower-case label of the remote node in the service graph, such as "favstar". Avoid names with
     * variables or unique identifiers embedded.
     *
     * <p>When unset, the service name is derived from {@link Annotation.ServiceName} which is
     * often incorrectly set to the remote service name.
     *
     * <p>This is a primary label for trace lookup and aggregation, so it should be intuitive and
     * consistent. Many use a name from service discovery.
     */
    public Builder localServiceName(String localServiceName) {
      if (localServiceName == null) throw new NullPointerException("localServiceName == null");
      this.config.localServiceName = localServiceName;
      return this;
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

    /**
     * It is possible that later versions of finagle add new types of {@link Annotation}. If this
     * occurs, the values won't be mapped until an update occurs here. We increment a counter using
     * below if that occurs.
     *
     * @param stats defaults to scope "zipkin"
     */
    public Builder stats(StatsReceiver stats) {
      if (stats == null) throw new NullPointerException("stats == null");
      this.stats = stats;
      return this;
    }

    public ZipkinTracer build() {
      return new ZipkinTracer(spanReporter, config, stats);
    }
  }

  static final class StaticConfig implements Config {
    float initialSampleRate = zipkin.initialSampleRate$.Flag.apply();
    String localServiceName = zipkin.localServiceName$.Flag.apply();

    @Override public String localServiceName() {
      return localServiceName;
    }

    @Override public float initialSampleRate() {
      return initialSampleRate;
    }
  }
}
