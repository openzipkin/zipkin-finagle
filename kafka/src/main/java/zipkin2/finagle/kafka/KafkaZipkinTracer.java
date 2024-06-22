/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
package zipkin2.finagle.kafka;

import com.google.auto.service.AutoService;
import com.google.auto.value.AutoValue;
import com.twitter.finagle.stats.DefaultStatsReceiver$;
import com.twitter.finagle.stats.NullStatsReceiver;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Annotation;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Closables;
import com.twitter.util.Future;
import com.twitter.util.Time;
import java.net.InetSocketAddress;
import java.util.Iterator;
import scala.collection.mutable.StringBuilder;
import scala.runtime.BoxedUnit;
import zipkin.localServiceName$;
import zipkin2.reporter.Encoding;
import zipkin2.finagle.ZipkinTracer;
import zipkin2.reporter.kafka.KafkaSender;

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
    this(KafkaSender.newBuilder()
        .encoding(Encoding.JSON)
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
            kafka.close(); // TODO: blocking
            return Future.Done();
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
    /** Creates a builder with the correct defaults derived from global flags */
    public static Builder builder() {
      return new AutoValue_KafkaZipkinTracer_Config.Builder()
          .bootstrapServers(bootstrapServersFromFlag())
          .topic(zipkin.kafka.topic$.Flag.apply())
          .localServiceName(localServiceName$.Flag.apply())
          .initialSampleRate(zipkin.initialSampleRate$.Flag.apply());
    }

    static String bootstrapServersFromFlag() {
      StringBuilder result = new StringBuilder();
      for (Iterator<InetSocketAddress> i = zipkin.kafka.bootstrapServers$.Flag.apply().iterator();
          i.hasNext(); ) {
        InetSocketAddress next = i.next();
        result.append(next.getHostName()).append(':').append(next.getPort());
        if (i.hasNext()) result.append(',');
      }
      return result.toString();
    }

    abstract public Builder toBuilder();

    abstract String bootstrapServers();

    abstract String topic();

    @AutoValue.Builder
    public interface Builder {
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
      Builder localServiceName(String localServiceName);

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
