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
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

/**
 * Normally, Finagle configures the tracer implicitly and through flags. This implies constraints
 * needed for service loading. Alternatively, you can use this type to explicitly configure any
 * sender available in Zipkin.
 *
 * Ex.
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
 * tracer = ZipkinTracer.create(sender);
 * }</pre>
 *
 */
public final class BasicZipkinTracer extends ZipkinTracer {
  /** Note you must close the supplied sender externally, after this instance is closed. */
  public static ZipkinTracer create(Sender sender) {
    return new Builder(sender).build();
  }

  /** Like {@link #create(Sender)}, except you can configure settings such as the sampling rate. */
  public static Builder newBuilder(Sender sender) {
    return new Builder(sender);
  }

  BasicZipkinTracer(Builder builder) {
    super(AsyncReporter.builder(builder.sender)
        .metrics(new ReporterMetricsAdapter(builder.stats))
        .build(), builder.config, builder.stats);
  }

  public static final class Builder {
    final Sender sender;
    Config config = new Config();
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
      return new BasicZipkinTracer(this);
    }
  }

  static final class Config implements ZipkinTracer.Config {
    float initialSampleRate = zipkin.initialSampleRate$.Flag.apply();

    @Override public float initialSampleRate() {
      return initialSampleRate;
    }
  }
}
