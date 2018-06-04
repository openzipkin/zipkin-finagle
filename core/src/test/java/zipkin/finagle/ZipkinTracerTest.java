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
package zipkin.finagle;

import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.util.Time;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Span;
import zipkin.reporter.AsyncReporter;
import zipkin.reporter.Sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.Option.empty;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.root;
import static zipkin.finagle.FinagleTestObjects.seq;

public class ZipkinTracerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  BlockingQueue<List<Span>> spansSent = new LinkedBlockingDeque<>();

  ZipkinTracer tracer = newTracer(FakeSender.create().onSpans(spansSent::add));

  ZipkinTracer newTracer(Sender sender) {
    return new ZipkinTracer(AsyncReporter.builder(sender)
        .messageTimeout(0, TimeUnit.MILLISECONDS)
        .messageMaxBytes(176 + 5) // size of a simple span w/ 128-bit trace ID + list overhead
        .metrics(new ReporterMetricsAdapter(stats))
        .build(), () -> 1.0f, stats);
  }

  @After
  public void closeTracer() throws Exception {
    tracer.close();
  }

  @Test public void unfinishedSpansArentImplicitlyReported() throws Exception {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));

    tracer.reporter.flush();

    assertThat(spansSent.take()).isEmpty();
  }

  @Test public void finishedSpansAreImplicitlyReported() throws Exception {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));

    // client receive reports the span
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.reporter.flush();

    assertThat(spansSent.take().stream())
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  /** See {@link com.twitter.finagle.tracing.traceId128Bit$} */
  @Test public void traceId128Bit() throws Exception {
    TraceId root = new TraceId(
        SpanId.fromString("0f28590523a46541"),
        empty(),
        SpanId.fromString("0f28590523a46541").get(),
        empty(),
        Flags$.MODULE$.apply(),
        SpanId.fromString("d2f9288a2904503d")
    );

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));

    // client receive reports the span
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.reporter.flush();

    assertThat(spansSent.take().stream())
        .extracting(Span::traceIdString)
        .containsExactly("d2f9288a2904503d0f28590523a46541");
  }

  @Test
  public void reportIncrementsAcceptedMetrics() throws Exception {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.reporter.flush();

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("span_bytes"), 165L),
        entry(seq("spans"), 1L),
        entry(seq("message_bytes"), 170L),
        entry(seq("messages"), 1L)
    );
  }

  @Test
  public void incrementsDropMetricsOnSendError() throws Exception {
    tracer.close();
    tracer = newTracer(FakeSender.create().onSpans(span -> {
      throw new IllegalStateException(new NullPointerException());
    }));

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.reporter.flush();

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
}
