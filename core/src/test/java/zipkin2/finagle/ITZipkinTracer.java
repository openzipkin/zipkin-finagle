/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
import com.twitter.finagle.tracing.Annotation.ServerRecv$;
import com.twitter.finagle.tracing.Annotation.ServerSend$;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import java.util.List;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import zipkin2.DependencyLink;
import zipkin2.Endpoint;
import zipkin2.Span;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.internal.DependencyLinker;

import static com.twitter.util.Time.fromMilliseconds;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.awaitility.Awaitility.await;
import static scala.Option.empty;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.child;
import static zipkin2.finagle.FinagleTestObjects.root;

public abstract class ITZipkinTracer {
  protected InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  protected ZipkinTracer tracer;

  @AfterEach public void closeTracer() {
    tracer.close();
    stats.clear();
  }

  @BeforeEach public void createTracer() {
    tracer = newTracer("unknown");
  }

  protected abstract ZipkinTracer newTracer(String localServiceName);

  protected abstract List<List<Span>> getTraces() throws Exception;

  /** v2 json is default, though proto3 and thrift are possible */
  protected SpanBytesEncoder encoder() {
    return SpanBytesEncoder.JSON_V2;
  }

  int messageSizeInBytes(List<byte[]> encodedSpans) {
    return encoder().encoding().listSizeInBytes(encodedSpans);
  }

  @Test public void multipleSpansGoIntoSameMessage() {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), ServerRecv$.MODULE$, empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), ServerSend$.MODULE$, empty()));

    tracer.record(new Record(child, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    Endpoint web = Endpoint.newBuilder().serviceName("web").ip("127.0.0.1").build();
    Span server = Span.newBuilder()
        .traceId(root.traceId().toString())
        .id(root.spanId().toLong())
        .name("get")
        .timestamp(TODAY * 1000)
        .duration(1000L)
        .kind(Span.Kind.SERVER)
        .localEndpoint(web)
        .build();

    Span client = server.toBuilder()
        .kind(Span.Kind.CLIENT)
        .parentId(child.parentId().toLong())
        .id(child.spanId().toLong()).build();

    long expectedSpanBytes = encoder().sizeInBytes(server) + encoder().sizeInBytes(client);
    long expectedMessageSize =
        messageSizeInBytes(asList(encoder().encode(server), encoder().encode(client)));

    // the AsyncReporter thread has a default interval of 1s, but reporting delay can take longer
    await().atMost(5, SECONDS).untilAsserted(() -> {
      assertThat(getTraces()).containsExactly(asList(server, client));

      assertThat(mapAsJavaMap(stats.counters())).containsExactly(
          entry(FinagleTestObjects.seq("span_bytes"), expectedSpanBytes),
          entry(FinagleTestObjects.seq("spans"), 2L),
          entry(FinagleTestObjects.seq("spans_dropped"), 0L),
          entry(FinagleTestObjects.seq("message_bytes"), expectedMessageSize),
          entry(FinagleTestObjects.seq("messages"), 1L)
      );
    });
  }

  @Test public void configOverridesLocalServiceName_client() {
    tracer.close();
    tracer = newTracer("web");

    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), ServerRecv$.MODULE$, empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), ServerSend$.MODULE$, empty()));

    // Here we simulate someone setting the client ServiceName to the remote host
    tracer.record(new Record(child, fromMilliseconds(TODAY), new ServiceName("app"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), ClientSend$.MODULE$, empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, empty()));

    // the AsyncReporter thread has a default interval of 1s, but reporting delay can take longer
    await().atMost(5, SECONDS).untilAsserted(() -> assertThat(getTraces())
        .isNotEmpty().first()
        .extracting(t -> new DependencyLinker().putTrace(t).link())
        .asInstanceOf(InstanceOfAssertFactories.list(DependencyLink.class))
        .containsExactly(
            DependencyLink.newBuilder().parent("web").child("app").callCount(1).build()
        )
    );
  }
}
