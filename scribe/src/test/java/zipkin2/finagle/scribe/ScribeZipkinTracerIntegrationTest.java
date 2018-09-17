/*
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin2.finagle.scribe;

import com.twitter.finagle.tracing.Annotation.ClientRecv$;
import com.twitter.finagle.tracing.Annotation.ClientSend$;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.Option;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.scribe.ScribeCollector;
import zipkin2.finagle.ZipkinTracer;
import zipkin2.finagle.ZipkinTracerIntegrationTest;
import zipkin2.finagle.scribe.ScribeZipkinTracer.Config;
import zipkin2.storage.InMemoryStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.root;
import static zipkin2.finagle.FinagleTestObjects.seq;

public class ScribeZipkinTracerIntegrationTest extends ZipkinTracerIntegrationTest {
  final Option<Duration> none = Option.empty(); // avoid having to force generics

  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  ScribeCollector scribe;

  @Before
  public void start() {
    scribe = ScribeCollector.newBuilder().storage(storage).build();
    scribe.start();
  }

  @After
  public void close() {
    scribe.close();
  }

  Config config = Config.builder().initialSampleRate(1.0f).host("127.0.0.1:9410").build();

  @Override
  protected ZipkinTracer newTracer() {
    return new ScribeZipkinTracer(config, stats);
  }

  @Override
  protected List<List<Span>> getTraces() {
    return storage.getTraces();
  }

  /** Scribe can only use thrift */
  @Override
  protected SpanBytesEncoder encoder() {
    return SpanBytesEncoder.THRIFT;
  }

  @Test
  public void whenScribeIsDown() throws Exception {
    scribe.close();

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, none));

    Thread.sleep(1500); // wait for scribe request attempt to go through

    assertThat(mapAsJavaMap(stats.counters()))
        .containsOnly(
            entry(seq("spans"), 1L),
            entry(seq("span_bytes"), 165L),
            entry(seq("spans_dropped"), 1L),
            entry(seq("messages"), 1L),
            entry(seq("message_bytes"), 170L),
            entry(seq("messages_dropped"), 1L),
            entry(seq("messages_dropped", "com.twitter.finagle.Failure"), 1L),
            entry(
                seq(
                    "messages_dropped",
                    "com.twitter.finagle.Failure",
                    "com.twitter.finagle.ConnectionFailedException"),
                1L),
            entry(
                seq(
                    "messages_dropped",
                    "com.twitter.finagle.Failure",
                    "com.twitter.finagle.ConnectionFailedException",
                    "io.netty.channel.AbstractChannel$AnnotatedConnectException"),
                1L),
            entry(
                seq(
                    "messages_dropped",
                    "com.twitter.finagle.Failure",
                    "com.twitter.finagle.ConnectionFailedException",
                    "io.netty.channel.AbstractChannel$AnnotatedConnectException",
                    "io.netty.channel.unix.Errors$NativeConnectException"),
                1L));
  }
}
