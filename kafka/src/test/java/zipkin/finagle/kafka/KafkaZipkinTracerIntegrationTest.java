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

import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import scala.collection.Seq;
import zipkin.Codec;
import zipkin.Span;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerIntegrationTest;
import zipkin.finagle.kafka.KafkaZipkinTracer.Config;
import zipkin.reporter.kafka08.KafkaSender;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.root;
import static zipkin.finagle.FinagleTestObjects.seq;

public class KafkaZipkinTracerIntegrationTest extends ZipkinTracerIntegrationTest {

  final Option<Duration> none = Option.empty(); // avoid having to force generics
  EphemeralKafkaBroker broker = EphemeralKafkaBroker.create();
  @Rule public KafkaJunitRule kafka = new KafkaJunitRule(broker).waitForStartup();

  Config config;

  @Before public void createTracer() {
    config = Config.builder()
        .bootstrapServers(broker.getBrokerList().get())
        .initialSampleRate(1.0f).build();
    super.createTracer();
  }

  @Override protected ZipkinTracer newTracer() {
    return new KafkaZipkinTracer(config, stats);
  }

  @Override protected List<List<Span>> getTraces() throws Exception {
    KafkaConsumer<byte[], byte[]> consumer = kafka.helper().createByteConsumer();
    return kafka.helper().consume(config.topic(), consumer, 1).get()
        .stream()
        .map(ConsumerRecord::value)
        .map(Codec.THRIFT::readSpans)
        .collect(toList());
  }

  @Test
  public void whenKafkaIsDown() throws Exception {
    broker.stop();

    // Make a new tracer that fails faster than 60 seconds
    tracer.close();
    stats.clear();
    Map<String, String> overrides = new LinkedHashMap<>();
    overrides.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "100");
    tracer = new KafkaZipkinTracer(KafkaSender.builder()
        .bootstrapServers(config.bootstrapServers())
        .topic(config.topic())
        .overrides(overrides)
        .build(), config, stats);

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), none));

    Thread.sleep(1500); // wait for kafka request attempt to go through

    Map<Seq<String>, Object> map = mapAsJavaMap(stats.counters());
    assertThat(map.get(seq("spans"))).isEqualTo(1);
    assertThat(map.get(seq("span_bytes"))).isEqualTo(165);
    assertThat(map.get(seq("spans_dropped"))).isEqualTo(1);
    assertThat(map.get(seq("messages"))).isEqualTo(1);
    assertThat(map.get(seq("message_bytes"))).isEqualTo(170);
    assertThat(map.get(seq("messages_dropped"))).isEqualTo(1);
    assertThat(map.get(seq("messages_dropped", "org.apache.kafka.common.errors.TimeoutException"))).isEqualTo(1);

    assertThat(map.size()).isEqualTo(7);
  }
}
