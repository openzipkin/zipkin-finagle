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
package zipkin2.finagle.kafka;

import com.github.charithe.kafka.EphemeralKafkaBroker;
import com.github.charithe.kafka.KafkaJunitRule;
import com.twitter.finagle.tracing.Annotation.ClientRecv$;
import com.twitter.finagle.tracing.Annotation.ClientSend$;
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
import zipkin2.Span;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.finagle.FinagleTestObjects;
import zipkin2.finagle.ITZipkinTracer;
import zipkin2.finagle.ZipkinTracer;
import zipkin2.reporter.kafka.KafkaSender;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static scala.collection.JavaConverters.mapAsJavaMap;

public class ITKafkaZipkinTracer extends ITZipkinTracer {
  final Option<Duration> none = Option.empty(); // avoid having to force generics
  EphemeralKafkaBroker broker = EphemeralKafkaBroker.create();
  @Rule public KafkaJunitRule kafka = new KafkaJunitRule(broker).waitForStartup();

  KafkaZipkinTracer.Config config;

  @Before public void createTracer() {
    config = KafkaZipkinTracer.Config.builder()
        .bootstrapServers(broker.getBrokerList().get())
        .initialSampleRate(1.0f).build();
    super.createTracer();
  }

  @Override protected ZipkinTracer newTracer(String localServiceName) {
    config = config.toBuilder().localServiceName(localServiceName).build();
    return new KafkaZipkinTracer(config, stats);
  }

  @Override protected List<List<Span>> getTraces() throws Exception {
    KafkaConsumer<byte[], byte[]> consumer = kafka.helper().createByteConsumer();
    return kafka.helper().consume(config.topic(), consumer, 1).get()
        .stream()
        .map(ConsumerRecord::value)
        .map(SpanBytesDecoder.JSON_V2::decodeList)
        .collect(toList());
  }

  @Test public void whenKafkaIsDown() throws Exception {
    broker.stop();

    // Make a new tracer that fails faster than 60 seconds
    tracer.close();
    stats.clear();
    Map<String, String> overrides = new LinkedHashMap<>();
    overrides.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, "100");
    tracer = new KafkaZipkinTracer(KafkaSender.newBuilder()
        .bootstrapServers(config.bootstrapServers())
        .topic(config.topic())
        .overrides(overrides)
        .build(), config, stats);

    tracer.record(
        new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY),
            new ServiceName("web"), none));
    tracer.record(
        new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY),
            new Rpc("get"), none));
    tracer.record(
        new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY),
            ClientSend$.MODULE$, none));
    tracer.record(new Record(
        FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY + 1),
        ClientRecv$.MODULE$, none));

    // wait for the HTTP request attempt to go through
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(mapAsJavaMap(stats.counters()))
        .containsOnly(
            entry(FinagleTestObjects.seq("spans"), 1L),
            entry(FinagleTestObjects.seq("span_bytes"), 185L),
            entry(FinagleTestObjects.seq("spans_dropped"), 1L),
            entry(FinagleTestObjects.seq("messages"), 1L),
            entry(FinagleTestObjects.seq("message_bytes"), 187L),
            entry(FinagleTestObjects.seq("messages_dropped"), 1L),
            entry(FinagleTestObjects.seq("messages_dropped",
                "org.apache.kafka.common.errors.TimeoutException"), 1L)
        ));
  }
}
