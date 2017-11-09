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
package zipkin.finagle;

import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Time;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import scala.collection.Seq;
import zipkin.Annotation;
import zipkin.Codec;
import zipkin.Endpoint;
import zipkin.Span;
import zipkin.reporter.Encoder;
import zipkin.reporter.Encoding;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.Option.empty;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static zipkin.Constants.CLIENT_RECV;
import static zipkin.Constants.CLIENT_SEND;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.child;
import static zipkin.finagle.FinagleTestObjects.root;
import static zipkin.finagle.FinagleTestObjects.seq;

public abstract class ZipkinTracerIntegrationTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  protected InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  protected ZipkinTracer tracer;

  @After
  public void closeTracer() throws Exception {
    tracer.close();
    stats.clear();
  }

  @Before
  public void createTracer() {
    tracer = newTracer();
  }

  protected abstract ZipkinTracer newTracer();

  protected abstract List<List<Span>> getTraces() throws Exception;

  protected int messageSizeInBytes(List<byte[]> encodedSpans) {
    return Encoding.THRIFT.listSizeInBytes(encodedSpans);
  }

  @Test public void multipleSpansGoIntoSameMessage() throws Exception {
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.record(new Record(child, Time.fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(child, Time.fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(child, Time.fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(child, Time.fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    Thread.sleep(2000); // the AsyncReporter thread has a default interval of 1s

    Endpoint web = Endpoint.create("web", 127 << 24 | 1);
    Span span1 = Span.builder()
        .traceId(root.traceId().toLong())
        .id(root.spanId().toLong())
        .name("get")
        .timestamp(TODAY * 1000)
        .duration(1000L)
        .addAnnotation(Annotation.create(TODAY * 1000, CLIENT_SEND, web))
        .addAnnotation(Annotation.create((TODAY + 1) * 1000, CLIENT_RECV, web)).build();

    Span span2 = span1.toBuilder()
        .traceId(child.traceId().toLong())
        .parentId(child.parentId().toLong())
        .id(child.spanId().toLong()).build();

    assertThat(getTraces()).containsExactly(asList(span1, span2));

    int expectedSpanBytes = Codec.THRIFT.sizeInBytes(span1) + Codec.THRIFT.sizeInBytes(span2);
    int expectedMessageSize =
        messageSizeInBytes(asList(Encoder.THRIFT.encode(span1), Encoder.THRIFT.encode(span2)));

    Map<Seq<String>, Object> map = mapAsJavaMap(stats.counters());
    assertThat(map.get(seq("spans"))).isEqualTo(2);
    assertThat(map.get(seq("span_bytes"))).isEqualTo(expectedSpanBytes);
    assertThat(map.get(seq("messages"))).isEqualTo(1);
    assertThat(map.get(seq("message_bytes"))).isEqualTo(expectedMessageSize);

    assertThat(map.size()).isEqualTo(4);
  }
}
