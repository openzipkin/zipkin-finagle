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
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.NullTracer;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.finagle.zipkin.core.SamplingTracer;
import zipkin.finagle.ZipkinTracerFlags;

@AutoService(Tracer.class)
public final class HttpZipkinTracer extends SamplingTracer {

  @AutoValue
  public static abstract class Config {
    /**
     * Creates a builder with the correct defaults derived from {@link HttpZipkinTracerFlags flags}
     */
    public static Builder builder() {
      return new AutoValue_HttpZipkinTracer_Config.Builder()
          .host(HttpZipkinTracerFlags.host())
          .initialSampleRate(ZipkinTracerFlags.initialSampleRate());
    }

    public Builder toBuilder() {
      return new AutoValue_HttpZipkinTracer_Config.Builder(this);
    }

    abstract String host();

    abstract float initialSampleRate();

    @AutoValue.Builder
    public interface Builder {

      /** Zipkin server listening on http; also used as the Host header. */
      Builder host(String host);

      /** How much data to collect. Default sample rate 0.1%. Max is 1, min 0. */
      Builder initialSampleRate(float initialSampleRate);

      Config build();
    }
  }

  /**
   * Create a new instance with default configuration.
   *
   * @param host Zipkin server listening on http; also used as the Host header.
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

  /**
   * Default constructor for the service loader
   */
  public HttpZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.http"));
  }

  HttpZipkinTracer(Config config, StatsReceiver stats) {
    super(new HttpRawZipkinTracer(config.host(), stats), config.initialSampleRate());
  }
}
