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
import com.twitter.finagle.tracing.Annotation.ClientRecv$;
import com.twitter.finagle.tracing.Annotation.ClientSend$;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.finagle.tracing.TraceId$;
import com.twitter.finagle.tracing.traceId128Bit$;
import com.twitter.util.Time;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Span;
import zipkin2.reporter.AsyncReporter;
import zipkin2.reporter.Sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static scala.Option.empty;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.root;
import static zipkin2.finagle.FinagleTestObjects.seq;

public class ZipkinTracerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  BlockingQueue<List<Span>> spansSent = new LinkedBlockingDeque<>();

  ZipkinTracer.Config config = new ZipkinTracer.Config() {
    @Override public String localServiceName() {
      return "unknown";
    }

    @Override public float initialSampleRate() {
      return 1.0f;
    }
  };

  ZipkinTracer tracer = newTracer(FakeSender.create().onSpans(spansSent::add));

  ZipkinTracer newTracer(Sender sender) {
    return ZipkinTracer.newBuilder(AsyncReporter.builder(sender)
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .messageMaxBytes(176 + 5) // size of a simple span w/ 128-bit trace ID + list overhead
        .metrics(new ReporterMetricsAdapter(stats))
<<<<<<< HEAD
        .build())
        .initialSampleRate(1.0f)
        .stats(stats).build();
=======
        .build(), config, stats);
>>>>>>> Adds flag zipkin.localServiceName to override any annotations
  }

  @After
  public void closeTracer() {
    tracer.close();
  }

  @Test public void defaultIsLocalServiceNameFromAnnotation() throws Exception {
    recordClientSpan(root);

    assertThat(spansSent.take().stream())
        .flatExtracting(Span::localServiceName)
        .containsExactly("web");
  }

  @Test public void configOverridesLocalServiceName() throws Exception {
    config = new ZipkinTracer.Config() {
      @Override public String localServiceName() {
        return "favstar";
      }

      @Override public float initialSampleRate() {
        return 1.0f;
      }
    };
    tracer = newTracer(FakeSender.create().onSpans(spansSent::add));

    recordClientSpan(root);

    assertThat(spansSent.take().stream())
        .flatExtracting(Span::localServiceName)
        .containsExactly("favstar");
  }

  @Test public void unfinishedSpansArentImplicitlyReported() throws Exception {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    flush();

    assertThat(spansSent.take()).isEmpty();
  }

  @Test public void finishedSpansAreImplicitlyReported() throws Exception {
<<<<<<< HEAD
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    // client receive reports the span
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    flush();
=======
    recordClientSpan(root);
>>>>>>> Adds flag zipkin.localServiceName to override any annotations

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

<<<<<<< HEAD
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    // client receive reports the span
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    flush();
=======
    recordClientSpan(root);
>>>>>>> Adds flag zipkin.localServiceName to override any annotations

    assertThat(spansSent.take().stream())
        .extracting(Span::traceId)
        .containsExactly("d2f9288a2904503d0f28590523a46541");
  }

  @Test
<<<<<<< HEAD
  public void reportIncrementsAcceptedMetrics() {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    flush();
=======
  public void reportIncrementsAcceptedMetrics() throws Exception {
    recordClientSpan(root);
>>>>>>> Adds flag zipkin.localServiceName to override any annotations

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("span_bytes"), 165L),
        entry(seq("spans"), 1L),
        entry(seq("spans_dropped"),0L),
        entry(seq("message_bytes"), 170L),
        entry(seq("messages"), 1L)
    );
  }

  @Test
  public void incrementsDropMetricsOnSendError() {
    tracer.close();
    tracer = newTracer(FakeSender.create().onSpans(span -> {
      throw new IllegalStateException(new NullPointerException());
    }));

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

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

<<<<<<< HEAD
  void flush() {
    ((AsyncReporter) tracer.reporter).flush();
=======
  void recordClientSpan(TraceId traceId) {
    tracer.record(new Record(traceId, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(traceId, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(traceId, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));

    // client receive reports the span
    tracer.record(new Record(traceId, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    tracer.reporter.flush();
>>>>>>> Adds flag zipkin.localServiceName to override any annotations
  }
}
