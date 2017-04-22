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
package zipkin.finagle.scribe;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.Name;
import com.twitter.finagle.Resolver$;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Tracer;
import com.twitter.util.Future;
import com.twitter.util.Time;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerFlags;

@AutoService(Tracer.class)
public final class ScribeZipkinTracer extends ZipkinTracer {
  private final ScribeSender scribe;

  /**
   * Default constructor for the service loader
   */
  public ScribeZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.scribe"));
  }

  ScribeZipkinTracer(Config config, StatsReceiver stats) {
    this(new ScribeSender(config), config, stats);
  }

  private ScribeZipkinTracer(ScribeSender scribe, Config config, StatsReceiver stats) {
    super(scribe, config, stats);
    this.scribe = scribe;
  }

  /**
   * Create a new instance with default configuration.
   *
   * @param host The network location of the Zipkin scribe service
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static ScribeZipkinTracer create(String host, StatsReceiver stats) {
    return new ScribeZipkinTracer(Config.builder().host(host).build(), stats);
  }

  /**
   * @param config includes flush interval and scribe properties
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static ScribeZipkinTracer create(Config config, StatsReceiver stats) {
    return new ScribeZipkinTracer(config, stats);
  }

  @Override public Future<BoxedUnit> close(final Time deadline) {
    return scribe.closeFuture().flatMap(new AbstractFunction1<BoxedUnit, Future<BoxedUnit>>() {
      @Override public Future<BoxedUnit> apply(BoxedUnit v1) {
        return ScribeZipkinTracer.super.close(deadline);
      }
    });
  }

  @AutoValue
  public static abstract class Config implements ZipkinTracer.Config {
    /**
     * Creates a builder with the correct defaults derived from {@link ScribeZipkinTracerFlags flags}
     */
    public static Builder builder() {
      return new AutoValue_ScribeZipkinTracer_Config.Builder()
          .host(ScribeZipkinTracerFlags.host())
          .initialSampleRate(ZipkinTracerFlags.initialSampleRate());
    }

    abstract public Builder toBuilder();

    abstract Name host();

    @AutoValue.Builder
    public abstract static class Builder {
      /** The network location of the Scribe service. Defaults to "localhost:1463" */
      public abstract Builder host(Name host);

      /** Shortcut for a {@link #host(Name)} encoded as a String */
      public final Builder host(String host) {
        return host(Resolver$.MODULE$.eval(host));
      }

      /** @see ZipkinTracer.Config#initialSampleRate() */
      public abstract Builder initialSampleRate(float initialSampleRate);

      public abstract Config build();
    }
  }
}
