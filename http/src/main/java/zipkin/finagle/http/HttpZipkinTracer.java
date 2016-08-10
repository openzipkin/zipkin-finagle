/**
 * Copyright 2016 The OpenZipkin Authors
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
package zipkin.finagle.http;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.Name;
import com.twitter.finagle.Resolver$;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Closables;
import com.twitter.util.Future;
import com.twitter.util.Time;
import scala.runtime.BoxedUnit;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerFlags;

@AutoService(Tracer.class)
public final class HttpZipkinTracer extends ZipkinTracer {
  private final HttpSpanConsumer http;

  /**
   * Default constructor for the service loader
   */
  public HttpZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.http"));
  }

  HttpZipkinTracer(Config config, StatsReceiver stats) {
    this(new HttpSpanConsumer(config), config, stats);
  }

  private HttpZipkinTracer(HttpSpanConsumer http, Config config, StatsReceiver stats) {
    super(http, config, stats);
    this.http = http;
  }

  /**
   * Create a new instance with default configuration.
   *
   * @param host The network location of the Zipkin http service
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static HttpZipkinTracer create(String host, StatsReceiver stats) {
    return new HttpZipkinTracer(Config.builder().host(host).build(), stats);
  }

  /**
   * @param config includes flush interval and http properties
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static HttpZipkinTracer create(Config config, StatsReceiver stats) {
    return new HttpZipkinTracer(config, stats);
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    return Closables.sequence(http, new AbstractClosable() {
      @Override public Future<BoxedUnit> close(Time deadline) {
        return HttpZipkinTracer.super.close(deadline);
      }
    }).close(deadline);
  }

  @AutoValue
  public static abstract class Config implements ZipkinTracer.Config {
    /**
     * Creates a builder with the correct defaults derived from {@link HttpZipkinTracerFlags flags}
     */
    public static Builder builder() {
      return new AutoValue_HttpZipkinTracer_Config.Builder()
          .hostHeader(HttpZipkinTracerFlags.hostHeader())
          .host(HttpZipkinTracerFlags.host())
          .compressionEnabled(HttpZipkinTracerFlags.compressionEnabled())
          .initialSampleRate(ZipkinTracerFlags.initialSampleRate());
    }

    public Builder toBuilder() {
      return new AutoValue_HttpZipkinTracer_Config.Builder(this);
    }

    abstract Name host();

    abstract String hostHeader();

    abstract boolean compressionEnabled();

    @AutoValue.Builder
    public abstract static class Builder {
      /** The network location of the Zipkin http service. Defaults to "localhost:9411" */
      public abstract Builder host(Name host);

      /** Shortcut for a {@link #host(Name)} encoded as a String */
      public final Builder host(String host) {
        return host(Resolver$.MODULE$.eval(host));
      }

      /** The Host header used when sending spans to Zipkin. Defaults to "zipkin" */
      public abstract Builder hostHeader(String host);

      /** True implies that spans will be gzipped before transport. Defaults to true. */
      public abstract Builder compressionEnabled(boolean compressSpans);

      /** @see ZipkinTracer.Config#initialSampleRate() */
      public abstract Builder initialSampleRate(float initialSampleRate);

      public abstract Config build();
    }
  }
}
