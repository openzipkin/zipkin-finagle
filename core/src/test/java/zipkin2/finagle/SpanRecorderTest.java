/*
 * Copyright 2016-2022 The OpenZipkin Authors
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

import com.twitter.finagle.service.TimeoutFilter;
import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.util.Duration;
import com.twitter.util.MockTimer;
import com.twitter.util.Time;
import java.net.InetSocketAddress;
import java.util.Date;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import zipkin2.Span;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.Option.empty;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.child;
import static zipkin2.finagle.FinagleTestObjects.root;
import static zipkin2.finagle.FinagleTestObjects.seq;

public class SpanRecorderTest {
  @Rule
  public WithTimeAt time = new WithTimeAt(TODAY);
  MockTimer timer = new MockTimer();

  InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  BlockingQueue<Span> spansSent = new LinkedBlockingDeque<>();
  SpanRecorder recorder;

  @Before
  public void setRecorder() {
    // Recorder schedules a flusher thread on instantiation. Do this in a Before block so
    // that we can control time.
    recorder = new SpanRecorder(span -> spansSent.add(span), stats, timer, "unknown");
  }

  /** This is replaying actual events that happened with Finagle's tracer */
  @Test public void exampleRootAndChild() throws InterruptedException {

    // Initiating a server-span based on an incoming request
    advanceAndRecord(0, root, new Annotation.Rpc("GET"));
    advanceAndRecord(4, root, new Annotation.BinaryAnnotation("http.uri", "/"));
    advanceAndRecord(15, root, new Annotation.ServiceName("frontend"));
    advanceAndRecord(0, root, new Annotation.BinaryAnnotation("srv/finagle.version", "6.36.0"));
    advanceAndRecord(0, root, Annotation.ServerRecv$.MODULE$);
    advanceAndRecord(1, root, new Annotation.LocalAddr(socketAddr("127.0.0.1", 8081)));
    advanceAndRecord(1, root, new Annotation.ServerAddr(socketAddr("127.0.0.1", 8081)));
    advanceAndRecord(1, root, new Annotation.ClientAddr(socketAddr("127.0.0.1", 58624)));

    // Creating a new child-span based on an outgoing request
    advanceAndRecord(3, child, new Annotation.Rpc("GET"));
    advanceAndRecord(0, child, new Annotation.BinaryAnnotation("http.uri", "/api"));
    advanceAndRecord(0, child, new Annotation.ServiceName("frontend"));
    advanceAndRecord(0, child, new Annotation.BinaryAnnotation("clnt/finagle.version", "6.36.0"));
    advanceAndRecord(0, child, Annotation.ClientSend$.MODULE$);
    advanceAndRecord(46, child, Annotation.WireSend$.MODULE$);
    advanceAndRecord(7, child, new Annotation.ServerAddr(socketAddr("127.0.0.1", 9000)));
    advanceAndRecord(1, child, new Annotation.ClientAddr(socketAddr("127.0.0.1", 58627)));
    advanceAndRecord(178, child, Annotation.WireRecv$.MODULE$);
    advanceAndRecord(2, child, Annotation.ClientRecv$.MODULE$);

    // Finishing the server span
    advanceAndRecord(40, root, Annotation.ServerSend$.MODULE$);

    Span clientSide = spansSent.take();
    Span serverSide = spansSent.take();

    assertThat(clientSide.kind()).isEqualTo(Span.Kind.CLIENT);
    assertThat(clientSide.annotations()).extracting(zipkin2.Annotation::value).containsExactly(
        "ws", "wr"
    );

    assertThat(serverSide.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(serverSide.annotations()).isEmpty();
  }

  @Test public void incrementsCounterWhenUnexpected_binaryAnnotation() throws Exception {
    recorder.record(
        new Record(root, Time.fromMilliseconds(TODAY),
            new Annotation.BinaryAnnotation("web", new Date()), empty())
    );

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("record", "unhandled", "java.util.Date"), 1L)
    );
  }

  /** Better to drop instead of crash on expected new Annotation types */
  class FancyAnnotation extends Annotation {

  }

  @Test public void incrementsCounterWhenUnexpected_annotation() throws Exception {
    recorder.record(
        new Record(root, Time.fromMilliseconds(TODAY), new FancyAnnotation(), empty())
    );

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("record", "unhandled", FancyAnnotation.class.getName()), 1L)
    );
  }

  @Test public void reportsSpanOn_ClientRecv() throws Exception {
    advanceAndRecord(0, root, Annotation.ClientSend$.MODULE$);
    advanceAndRecord(1, root, Annotation.ClientRecv$.MODULE$);

    Span span = spansSent.take();
    assertThat(span.kind()).isEqualTo(Span.Kind.CLIENT);
    assertThat(span.annotations()).isEmpty();
    assertThat(span.timestamp()).isEqualTo(TODAY * 1000);
    assertThat(span.duration()).isEqualTo(1000);
  }

  // @Test public void reportsSpanOn_Timeout() throws Exception {
  //   advanceAndRecord(0, root, Annotation.ClientSend$.MODULE$);
  //   advanceAndRecord(1, root, new Annotation.Message(TimeoutFilter.TimeoutAnnotation()));

  //   Span span = spansSent.take();
  //   assertThat(span.kind()).isEqualTo(Span.Kind.CLIENT);
  //   assertThat(span.annotations()).extracting(zipkin2.Annotation::value).containsExactly(
  //       "finagle.timeout"
  //   );
  //   assertThat(span.timestamp()).isEqualTo(TODAY * 1000);
  //   assertThat(span.duration()).isEqualTo(1000);
  // }

  @Test public void reportsSpanOn_ServerSend() throws Exception {
    advanceAndRecord(0, root, Annotation.ServerRecv$.MODULE$);
    advanceAndRecord(1, root, Annotation.ServerSend$.MODULE$);

    Span span = spansSent.take();
    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.annotations()).isEmpty();
    assertThat(span.timestamp()).isEqualTo(TODAY * 1000);
    assertThat(span.duration()).isEqualTo(1000);
  }

  /** ServiceName can be set late, but it should be consistent across annotations. */
  @Test public void serviceNameAppliesRetroactively() throws Exception {
    advanceAndRecord(0, root, new Annotation.Rpc("GET"));
    advanceAndRecord(0, root, Annotation.ServerRecv$.MODULE$);
    advanceAndRecord(0, root, new Annotation.ServiceName("frontend"));
    advanceAndRecord(15, root, Annotation.ServerSend$.MODULE$);

    Span span = spansSent.take();
    assertThat(span.localServiceName()).isEqualTo("frontend");
  }

  @Test public void flushesIncompleteSpans() throws Exception {
    advanceAndRecord(0, root, new Annotation.Rpc("GET"));
    advanceAndRecord(15, root, new Annotation.ServiceName("frontend"));
    advanceAndRecord(0, root, Annotation.ServerRecv$.MODULE$);
    // Note: there's no ServerSend() which would complete the span.

    time.advance(recorder.ttl.plus(Duration.fromMilliseconds(1))); // advance timer
    timer.tick(); // invokes a flush

    Span span = spansSent.take();
    assertThat(span.id()).isEqualTo(root.spanId().toString());
    assertThat(span.name()).isEqualTo("get");
    assertThat(span.kind()).isEqualTo(Span.Kind.SERVER);
    assertThat(span.annotations()).extracting(zipkin2.Annotation::value).containsExactly(
        "finagle.flush"
    );
    assertThat(span.duration()).isNull();
  }

  private void advanceAndRecord(int millis, TraceId traceId, Annotation annotation) {
    time.advance(Duration.fromMilliseconds(millis));
    recorder.record(new Record(traceId, Time.now(), annotation, empty()));
  }

  private InetSocketAddress socketAddr(String host, int port) {
    return new InetSocketAddress(host, port);
  }
}
