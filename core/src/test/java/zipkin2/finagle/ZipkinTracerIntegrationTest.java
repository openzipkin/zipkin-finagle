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
package zipkin2.finagle;

import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServerRecv;
import com.twitter.finagle.tracing.Annotation.ServerSend;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Time;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.Option.empty;
import static scala.collection.JavaConverters.mapAsJavaMap;

public abstract class ZipkinTracerIntegrationTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  protected InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  protected ZipkinTracer tracer;

  @After
  public void closeTracer() {
    tracer.close();
    stats.clear();
  }

  @Before
  public void createTracer() {
    tracer = newTracer();
  }

  protected abstract ZipkinTracer newTracer();

  protected abstract List<List<Span>> getTraces() throws Exception;

  /** v2 json is default, though proto3 and thrift are possible */
  protected SpanBytesEncoder encoder() {
    return SpanBytesEncoder.JSON_V2;
  }

  int messageSizeInBytes(List<byte[]> encodedSpans) {
    return encoder().encoding().listSizeInBytes(encodedSpans);
  }

  @Test public void multipleSpansGoIntoSameMessage() throws Exception {
    tracer.record(new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY), new ServerRecv(), empty()));
    tracer.record(new Record(
        FinagleTestObjects.root, Time.fromMilliseconds(FinagleTestObjects.TODAY + 1), new ServerSend(), empty()));

    tracer.record(new Record(
        FinagleTestObjects.child, Time.fromMilliseconds(FinagleTestObjects.TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(
        FinagleTestObjects.child, Time.fromMilliseconds(FinagleTestObjects.TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(
        FinagleTestObjects.child, Time.fromMilliseconds(FinagleTestObjects.TODAY), new ClientSend(), empty()));
    tracer.record(new Record(
        FinagleTestObjects.child, Time.fromMilliseconds(FinagleTestObjects.TODAY + 1), new ClientRecv(), empty()));

    Thread.sleep(2000); // the AsyncReporter thread has a default interval of 1s

    Endpoint web = Endpoint.newBuilder().serviceName("web").ip("127.0.0.1").build();
    Span span1 =
        Span.newBuilder()
            .traceId(FinagleTestObjects.root.traceId().toString())
            .id(FinagleTestObjects.root.spanId().toLong())
            .name("get")
            .timestamp(FinagleTestObjects.TODAY * 1000)
            .duration(1000L)
            .kind(Span.Kind.SERVER)
            .localEndpoint(web)
            .build();

    Span span2 = span1.toBuilder()
        .kind(Span.Kind.CLIENT)
        .parentId(FinagleTestObjects.child.parentId().toLong())
        .id(FinagleTestObjects.child.spanId().toLong()).build();

    assertThat(getTraces()).containsExactly(asList(span1, span2));

    long expectedSpanBytes = encoder().sizeInBytes(span1) + encoder().sizeInBytes(span2);
    long expectedMessageSize =
        messageSizeInBytes(asList(encoder().encode(span1), encoder().encode(span2)));

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(FinagleTestObjects.seq("span_bytes"), expectedSpanBytes),
        entry(FinagleTestObjects.seq("spans"), 2L),
        entry(FinagleTestObjects.seq("message_bytes"), expectedMessageSize),
        entry(FinagleTestObjects.seq("messages"), 1L)
    );
  }
}
