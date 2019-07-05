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

import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation;
import com.twitter.finagle.tracing.Annotation.ClientRecv$;
import com.twitter.finagle.tracing.Annotation.ClientSend$;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServerRecv$;
import com.twitter.finagle.tracing.Annotation.ServerSend$;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.TraceId$;
import com.twitter.finagle.tracing.traceId128Bit$;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;

import static com.twitter.util.Time.fromMilliseconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static scala.Option.empty;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.root;
import static zipkin2.finagle.FinagleTestObjects.seq;

public class ZipkinTracerTest {
  @Rule public ExpectedException thrown = ExpectedException.none();

  InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  BlockingQueue<List<Span>> spansSent = new LinkedBlockingDeque<>();

  // Initialize with the special keyword unknown which delegates to Annotation.ServiceName otherwise
  ZipkinTracer tracer = newTracer(reporterBuilder(spansSent::add), "unknown");

  AsyncReporter.Builder reporterBuilder(Consumer<List<Span>> onSpans) {
    return AsyncReporter.builder(FakeSender.create().onSpans(onSpans))
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .messageMaxBytes(176 + 5)  // size of a simple span w/ 128-bit trace ID + list overhead
        .metrics(new ReporterMetricsAdapter(stats));
  }

  ZipkinTracer newTracer(AsyncReporter.Builder spanReporter, String localServiceName) {
    if (tracer != null) tracer.close();
    return ZipkinTracer.newBuilder(spanReporter.build())
        .initialSampleRate(1.0f)
        .localServiceName(localServiceName)
        .stats(stats)
        .build();
  }

  @After public void closeTracer() {
    tracer.close();
  }

  @Test public void defaultIsLocalServiceNameFromAnnotation() throws Exception {
    recordClientSpan(root);

    assertThat(spansSent.take().stream())
        .flatExtracting(Span::localServiceName)
        .containsExactly("web");
  }

  @Test public void configOverridesLocalServiceName_client() throws Exception {
    tracer = spanReporter("favstar");

    recordClientSpan(root);

    // Moves recorded service name to the remote service name
    assertThat(spansSent.take().stream())
        .flatExtracting(Span::localServiceName, Span::remoteServiceName)
        .containsExactly("favstar", "web");
  }

  @Test public void configOverridesLocalServiceName_client_combines() throws Exception {
    tracer = spanReporter("favstar");

    tracer.record(new Record(root, fromMilliseconds(TODAY), new Annotation.LocalAddr(
        new InetSocketAddress("1.2.3.3", 443)), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Annotation.ServerAddr(
        new InetSocketAddress("1.2.3.4", 80)), empty()));
    recordClientSpan(root);

    Span span = spansSent.take().get(0);
    // Combines the recorded service name with the remote IP and socket info
    assertThat(span.localEndpoint())
        .isEqualTo(Endpoint.newBuilder().serviceName("favstar").ip("1.2.3.3").port(443).build());
    assertThat(span.remoteEndpoint())
        .isEqualTo(Endpoint.newBuilder().serviceName("web").ip("1.2.3.4").port(80).build());
  }

  @Test public void configOverridesLocalServiceName_server() throws Exception {
    tracer = spanReporter("favstar");

    recordServerSpan(root);

    // Doesn't set remote service name
    assertThat(spansSent.take().stream())
        .flatExtracting(Span::localServiceName, Span::remoteServiceName)
        .containsExactly("favstar", null);
  }

  @Test public void unfinishedSpansArentImplicitlyReported() throws Exception {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    flush();

    assertThat(spansSent.take()).isEmpty();
  }

  @Test public void finishedSpansAreImplicitlyReported() throws Exception {
    recordClientSpan(root);

    assertThat(spansSent.take().stream())
        .flatExtracting(Span::kind)
        .containsExactly(Span.Kind.CLIENT);
  }

  /** See {@link traceId128Bit$} */
  @Test public void traceId128Bit() throws Exception {
    TraceId root = TraceId$.MODULE$.apply(
        SpanId.fromString("0f28590523a46541"),
        empty(),
        SpanId.fromString("0f28590523a46541").get(),
        empty(),
        Flags$.MODULE$.apply(),
        SpanId.fromString("d2f9288a2904503d"),
        false
    );

    recordClientSpan(root);

    assertThat(spansSent.take().stream())
        .extracting(Span::traceId)
        .containsExactly("d2f9288a2904503d0f28590523a46541");
  }

  @Test public void reportIncrementsAcceptedMetrics() {
    recordClientSpan(root);

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("span_bytes"), 165L),
        entry(seq("spans"), 1L),
        entry(seq("spans_dropped"), 0L),
        entry(seq("message_bytes"), 170L),
        entry(seq("messages"), 1L)
    );
  }

  @Test public void incrementsDropMetricsOnSendError() {
    tracer = newTracer(reporterBuilder(span -> {
      throw new IllegalStateException(new NullPointerException());
    }), "unknown");

    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    try {
      flush();
      failBecauseExceptionWasNotThrown(IllegalStateException.class);
    } catch (IllegalStateException e) {
    }

    assertThat(mapAsJavaMap(stats.counters())).containsOnly(
        entry(seq("spans"), 1L),
        entry(seq("span_bytes"), 165L),
        entry(seq("spans_dropped"), 1L),
        entry(seq("messages"), 1L),
        entry(seq("message_bytes"), 170L),
        entry(seq("messages_dropped"), 1L),
        entry(seq("messages_dropped", "java.lang.IllegalStateException"), 1L),
        entry(seq("messages_dropped", "java.lang.IllegalStateException",
            "java.lang.NullPointerException"), 1L)
    );
  }

  void flush() {
    ((AsyncReporter) tracer.reporter).flush();
  }

  ZipkinTracer spanReporter(String localServiceName) {
    return newTracer(reporterBuilder(spansSent::add)
            .messageMaxBytes(500), // RPC spans are bigger than local ones
        localServiceName);
  }

  void recordClientSpan(TraceId traceId) {
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    // client receive reports the span
    tracer.record(new Record(traceId, fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    flush();
  }

  void recordServerSpan(TraceId traceId) {
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), ServerRecv$.MODULE$, empty()));

    // server send reports the span
    tracer.record(new Record(traceId, fromMilliseconds(TODAY + 1), ServerSend$.MODULE$, empty()));

    flush();
  }
}
