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

import com.github.charithe.kafka.KafkaJunitRule;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import kafka.serializer.DefaultDecoder;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import zipkin.Codec;
import zipkin.Span;
import zipkin.finagle.RawZipkinTracer;
import zipkin.finagle.RawZipkinTracerTest;
import zipkin.finagle.kafka.KafkaZipkinTracer.Config;

import static com.twitter.util.Time.fromMilliseconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConversions.mapAsJavaMap;

public class KafkaRawZipkinTracerTest extends RawZipkinTracerTest {

  @Rule
  public KafkaJunitRule kafka = new KafkaJunitRule();

  Config config = Config.builder("localhost:" + kafka.kafkaBrokerPort()).build();

  @After
  public void closeProducer() {
    ((KafkaRawZipkinTracer) tracer).producer.close();
  }

  @Override protected RawZipkinTracer newTracer(StatsReceiver stats) {
    Producer<byte[], byte[]> producer = new KafkaProducer<>(config.kafkaProperties());
    return new KafkaRawZipkinTracer(producer, config.topic(), stats);
  }

  @Override protected List<List<Span>> getTraces() throws Exception {
    try {
      return kafka.readMessages(config.topic(), 1,
          new DefaultDecoder(kafka.consumerConfig().props()))
          .stream()
          .map(Codec.THRIFT::readSpans)
          .collect(Collectors.toList());
    } catch (TimeoutException e) {
      return Collections.emptyList();
    }
  }

  // TODO: the current producer blocks the calling thread on tracer.record
  // https://github.com/openzipkin/zipkin-finagle/issues/4
  @Test
  public void whenKafkaIsDown() throws Exception {
    closeProducer(); // close old producer

    // Make a new tracer that fails faster than 60 seconds
    Properties props = config.kafkaProperties();
    props.put(ProducerConfig.METADATA_FETCH_TIMEOUT_CONFIG, Integer.toString(100));
    config = config.toBuilder().kafkaProperties(props).build();
    tracer = newTracer(stats);

    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ClientSend(), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY + 1), new ClientRecv(), none));

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("log_span", "error", "org.apache.kafka.common.errors.TimeoutException"), 1)
    );
  }

  final Option<Duration> none = Option.empty(); // avoid having to force generics
}
