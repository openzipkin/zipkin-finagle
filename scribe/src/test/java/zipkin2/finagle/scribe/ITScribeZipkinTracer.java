/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import scala.Option;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.collector.scribe.ScribeCollector;
import zipkin2.finagle.ITZipkinTracer;
import zipkin2.finagle.ZipkinTracer;
import zipkin2.finagle.scribe.ScribeZipkinTracer.Config;
import zipkin2.storage.InMemoryStorage;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.root;
import static zipkin2.finagle.FinagleTestObjects.seq;

public class ITScribeZipkinTracer extends ITZipkinTracer {
  final Option<Duration> none = Option.empty(); // avoid having to force generics

  InMemoryStorage storage = InMemoryStorage.newBuilder().build();
  ScribeCollector scribe;

  @BeforeEach public void start() {
    scribe = ScribeCollector.newBuilder().storage(storage).build();
    scribe.start();
  }

  @AfterEach public void close() {
    scribe.close();
  }

  Config config = Config.builder().initialSampleRate(1.0f).host("127.0.0.1:9410").build();

  @Override protected ZipkinTracer newTracer(String localServiceName) {
    config = config.toBuilder().localServiceName(localServiceName).build();
    return new ScribeZipkinTracer(config, stats);
  }

  @Override protected List<List<Span>> getTraces() {
    return storage.getTraces();
  }

  /** Scribe can only use thrift */
  @Override protected SpanBytesEncoder encoder() {
    return SpanBytesEncoder.THRIFT;
  }

  @Test public void whenScribeIsDown() throws Exception {
    scribe.close();

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, none));

    // wait for the Scribe request attempt to go through
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(mapAsJavaMap(stats.counters()))
        .contains(
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
                1L)));
  }
}
