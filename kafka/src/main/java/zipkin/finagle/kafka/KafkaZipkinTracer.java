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
package zipkin.finagle.kafka;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.zipkin.core.SamplingTracer;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import zipkin.finagle.ZipkinTracerFlags;

@AutoService(com.twitter.finagle.tracing.Tracer.class)
public final class KafkaZipkinTracer extends SamplingTracer {

  @AutoValue
  public static abstract class Config {
    /**
     * Creates a builder with the correct defaults derived from {@link KafkaZipkinTracerFlags flags}
     */
    public static Builder builder() {
      return builder(KafkaZipkinTracerFlags.bootstrapServers());
    }

    /**
     * Creates a builder with the correct defaults derived from {@link KafkaZipkinTracerFlags flags}
     *
     * @param bootstrapServers overrides {@link KafkaZipkinTracerFlags#bootstrapServers()}
     */
    public static Builder builder(String bootstrapServers) {
      Properties props = new Properties();
      props.put("bootstrap.servers", bootstrapServers);
      props.put("key.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
      return new AutoValue_KafkaZipkinTracer_Config.Builder()
          .kafkaProperties(props)
          .topic(KafkaZipkinTracerFlags.topic())
          .initialSampleRate(ZipkinTracerFlags.initialSampleRate());
    }

    public Builder toBuilder() {
      return new AutoValue_KafkaZipkinTracer_Config.Builder(this);
    }

    abstract Properties kafkaProperties();

    abstract String topic();

    abstract float initialSampleRate();

    @AutoValue.Builder
    public interface Builder {
      /**
       * Configuration for Kafka producer. Essential configuration properties are:
       * bootstrap.servers, key.serializer, value.serializer. For a full list of config options, see
       * http://kafka.apache.org/082/documentation.html#producerconfigs
       */
      Builder kafkaProperties(Properties kafkaProperties);

      /** Sets kafka-topic for zipkin to report to. Default topic zipkin. */
      Builder topic(String topic);

      /** How much data to collect. Default sample rate 0.1%. Max is 1, min 0. */
      Builder initialSampleRate(float initialSampleRate);

      Config build();
    }
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
    return new KafkaZipkinTracer(Config.builder(bootstrapServers).build(), stats);
  }

  /**
   * @param config includes flush interval and kafka properties
   * @param stats Gets notified when spans are accepted or dropped. If you are not interested in
   * these events you can use {@linkplain NullStatsReceiver}
   */
  public static KafkaZipkinTracer create(Config config, StatsReceiver stats) {
    return new KafkaZipkinTracer(config, stats);
  }

  /**
   * Default constructor for the service loader
   */
  public KafkaZipkinTracer() {
    this(Config.builder().build(), DefaultStatsReceiver$.MODULE$.get().scope("zipkin.kafka"));
  }

  KafkaZipkinTracer(Config config, StatsReceiver stats) {
    super(new KafkaRawZipkinTracer(new KafkaProducer<byte[],byte[]>(config.kafkaProperties()),
        config.topic(), stats), config.initialSampleRate());
  }
}
