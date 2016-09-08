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
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import kafka.serializer.DefaultDecoder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import zipkin.Codec;
import zipkin.Span;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerTest;
import zipkin.finagle.kafka.KafkaZipkinTracer.Config;

import static com.twitter.util.Time.fromMilliseconds;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.root;

public class KafkaZipkinTracerTest extends ZipkinTracerTest {

  final Option<Duration> none = Option.empty(); // avoid having to force generics
  @Rule
  public KafkaJunitRule kafka = new KafkaJunitRule();
  Config config = Config.builder()
      .bootstrapServers("localhost:" + kafka.kafkaBrokerPort())
      .initialSampleRate(1.0f).build();

  @Override protected ZipkinTracer newTracer() {
    return new KafkaZipkinTracer(config, stats);
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

  @Ignore @Test(timeout = 1000) // TODO: https://github.com/openzipkin/zipkin-finagle/issues/4
  public void whenKafkaIsDown() throws Exception {
    kafka.shutdownKafka();

    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), none));
  }
}
