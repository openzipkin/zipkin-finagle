/*
 * Copyright 2016-2020 The OpenZipkin Authors
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
package zipkin2.finagle.http;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.Name;
import com.twitter.finagle.Resolver$;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Annotation;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.util.Future;
import com.twitter.util.Time;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.localServiceName$;
import zipkin2.finagle.ZipkinTracer;

@AutoService(Tracer.class)
public final class HttpZipkinTracer extends ZipkinTracer {
  private final HttpSender http;

  /**
   * Default constructor for the service loader
   */
  public HttpZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.http"));
  }

  HttpZipkinTracer(Config config, StatsReceiver stats) {
    this(new HttpSender(config), config, stats);
  }

  private HttpZipkinTracer(HttpSender http, Config config, StatsReceiver stats) {
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

  @Override public Future<BoxedUnit> close(final Time deadline) {
    return http.closeFuture().flatMap(new AbstractFunction1<BoxedUnit, Future<BoxedUnit>>() {
      @Override public Future<BoxedUnit> apply(BoxedUnit v1) {
        return HttpZipkinTracer.super.close(deadline);
      }
    });
  }

  @AutoValue
  public static abstract class Config implements ZipkinTracer.Config {
    /** Creates a builder with the correct defaults derived from global flags */
    public static Builder builder() {
      return new AutoValue_HttpZipkinTracer_Config.Builder()
          .hostHeader(zipkin.http.hostHeader$.Flag.apply())
          .host(zipkin.http.host$.Flag.apply())
          .path(zipkin.http.path$.Flag.apply())
          .compressionEnabled(zipkin.http.compressionEnabled$.Flag.apply())
          .tlsEnabled(zipkin.http.tlsEnabled$.Flag.apply())
          .tlsValidationEnabled(zipkin.http.tlsValidationEnabled$.Flag.apply())
          .localServiceName(localServiceName$.Flag.apply())
          .initialSampleRate(zipkin.initialSampleRate$.Flag.apply());
    }

    abstract public Builder toBuilder();

    abstract Name host();

    abstract String hostHeader();

    abstract boolean tlsEnabled();

    abstract boolean tlsValidationEnabled();

    abstract boolean compressionEnabled();

    abstract String path();

    @AutoValue.Builder
    public abstract static class Builder {
      /**
       * Lower-case label of the remote node in the service graph, such as "favstar". Avoid names
       * with variables or unique identifiers embedded.
       *
       * <p>When unset, the service name is derived from {@link Annotation.ServiceName} which is
       * often incorrectly set to the remote service name.
       *
       * <p>This is a primary label for trace lookup and aggregation, so it should be intuitive and
       * consistent. Many use a name from service discovery.
       */
      public abstract Builder localServiceName(String localServiceName);

      /** The network location of the Zipkin http service. Defaults to "localhost:9411" */
      public abstract Builder host(Name host);

      /** The path to the Zipkin endpoint relative to the host. Defaults to "/api/v2/spans" */
      public abstract Builder path(String path);

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

      /** Whether or not the Zipkin host uses TLS. Defaults to false */
      public abstract Builder tlsEnabled(boolean tlsEnabled);

      /** Whether or not to enable TLS validation for the TLS-enabled Zipkin host */
      public abstract Builder tlsValidationEnabled(boolean tlsValidationEnabled);

      public abstract Config build();
    }
  }
}
