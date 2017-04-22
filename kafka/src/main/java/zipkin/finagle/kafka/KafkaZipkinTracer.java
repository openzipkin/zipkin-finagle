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
package zipkin.finagle.kafka;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Closables;
import com.twitter.util.Future;
import com.twitter.util.Time;
import java.io.IOException;
import scala.runtime.BoxedUnit;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerFlags;
import zipkin.reporter.kafka08.KafkaSender;

@AutoService(com.twitter.finagle.tracing.Tracer.class)
public final class KafkaZipkinTracer extends ZipkinTracer {

  private final KafkaSender kafka;

  /**
   * Default constructor for the service loader
   */
  public KafkaZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.kafka"));
  }

  KafkaZipkinTracer(Config config, StatsReceiver stats) {
    this(KafkaSender.builder()
        .bootstrapServers(config.bootstrapServers())
        .topic(config.topic())
        .build(), config, stats);
  }

  KafkaZipkinTracer(KafkaSender kafka, Config config, StatsReceiver stats) {
    super(kafka, config, stats);
    this.kafka = kafka;
  }

  /**
   * Create a new instance with default configuration.
   *
   * @param bootstrapServers A list of host/port pairs to use for establishing the initial
   * connection to the Kafka cluster. Like: host1:port1,host2:port2,... Does not to be all the
   * servers part of Kafka cluster.
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static KafkaZipkinTracer create(String bootstrapServers, StatsReceiver stats) {
    return new KafkaZipkinTracer(Config.builder().bootstrapServers(bootstrapServers).build(),
        stats);
  }

  /**
   * @param config includes flush interval and kafka properties
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static KafkaZipkinTracer create(Config config, StatsReceiver stats) {
    return new KafkaZipkinTracer(config, stats);
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    return Closables.sequence(
        new AbstractClosable() {
          @Override public Future<BoxedUnit> close(Time deadline) {
            try {
              kafka.close(); // TODO: blocking
              return Future.Done();
            } catch (IOException e) {
              return Future.exception(e);
            }
          }
        },
        new AbstractClosable() {
          @Override public Future<BoxedUnit> close(Time deadline) {
            return KafkaZipkinTracer.super.close(deadline);
          }
        }).close(deadline);
  }

  @AutoValue
  public static abstract class Config implements ZipkinTracer.Config {
    /**
     * Creates a builder with the correct defaults derived from {@link KafkaZipkinTracerFlags
     * flags}
     */
    public static Builder builder() {
      return new AutoValue_KafkaZipkinTracer_Config.Builder()
          .bootstrapServers(KafkaZipkinTracerFlags.bootstrapServers())
          .topic(KafkaZipkinTracerFlags.topic())
          .initialSampleRate(ZipkinTracerFlags.initialSampleRate());
    }

    abstract public Builder toBuilder();

    abstract String bootstrapServers();

    abstract String topic();

    @AutoValue.Builder
    public interface Builder {
      /**
       * Initial set of kafka servers to connect to, rest of cluster will be discovered (comma
       * separated). Default localhost:9092
       */
      Builder bootstrapServers(String bootstrapServers);

      /** Sets kafka-topic for zipkin to report to. Default topic zipkin. */
      Builder topic(String topic);

      /** @see ZipkinTracer.Config#initialSampleRate() */
      Builder initialSampleRate(float initialSampleRate);

      Config build();
    }
  }
}
