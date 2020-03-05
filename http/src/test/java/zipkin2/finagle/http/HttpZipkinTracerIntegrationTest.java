/*
 * Copyright 2016-2020 The OpenZipkin Authors
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
package zipkin2.finagle.http;

import com.twitter.finagle.tracing.Annotation.ClientRecv$;
import com.twitter.finagle.tracing.Annotation.ClientSend$;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import com.twitter.util.Time;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import zipkin.http.path$;
import zipkin2.Span;
import zipkin2.finagle.FinagleTestObjects;
import zipkin2.finagle.ZipkinTracer;
import zipkin2.finagle.ZipkinTracerIntegrationTest;
import zipkin2.finagle.http.HttpZipkinTracer.Config;
import zipkin2.junit.ZipkinRule;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConverters.mapAsJavaMap;
import static zipkin2.finagle.FinagleTestObjects.TODAY;
import static zipkin2.finagle.FinagleTestObjects.root;

public class HttpZipkinTracerIntegrationTest extends ZipkinTracerIntegrationTest {
  @Rule public ZipkinRule http = new ZipkinRule();
  String host = "localhost:" + URI.create(http.httpUrl()).getPort();
  Config config = Config.builder().initialSampleRate(1.0f).host(host).build();

  final Option<Duration> none = Option.empty(); // avoid having to force generics

  @Override protected ZipkinTracer newTracer(String localServiceName) {
    config = config.toBuilder().localServiceName(localServiceName).build();
    return new HttpZipkinTracer(config, stats);
  }

  @Override protected List<List<Span>> getTraces() {
    return http.getTraces();
  }

  @Test public void whenHttpIsDown() throws Exception {
    http.shutdown(); // shutdown the normal zipkin rule

    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, none));
    tracer.record(new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, none));

    Thread.sleep(1500); // wait for http request attempt to go through

    assertThat(mapAsJavaMap(stats.counters())).contains(
        entry(FinagleTestObjects.seq("spans"), 1L),
        entry(FinagleTestObjects.seq("span_bytes"), 185L),
        entry(FinagleTestObjects.seq("spans_dropped"), 1L),
        entry(FinagleTestObjects.seq("messages"), 1L),
        entry(FinagleTestObjects.seq("message_bytes"), 187L),
        entry(FinagleTestObjects.seq("messages_dropped"), 1L),
        entry(FinagleTestObjects.seq("messages_dropped", "com.twitter.finagle.Failure"), 1L),
        entry(FinagleTestObjects.seq("messages_dropped", "com.twitter.finagle.Failure",
            "com.twitter.finagle.ConnectionFailedException"), 1L),
        entry(FinagleTestObjects.seq("messages_dropped", "com.twitter.finagle.Failure",
            "com.twitter.finagle.ConnectionFailedException",
            "io.netty.channel.AbstractChannel$AnnotatedConnectException"), 1L)
    );
  }

  @Test public void path() throws Exception {
    http.shutdown(); // shutdown the normal zipkin rule

    // create instructions to create a complete RPC span
    List<Record> records = asList(
        new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none),
        new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none),
        new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, none),
        new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, none)
    );

    MockWebServer server = new MockWebServer();
    config = config.toBuilder().host("localhost:" + server.getPort()).build();
    try {
      List<RecordedRequest> requests = new ArrayList<>();
      String customPath = "/custom/api/v2/spans";
      for (Optional<String> overridePath : asList(Optional.<String>empty(),
          Optional.of(customPath))) {
        // recreate the tracer with the path configuration
        closeTracer();
        overridePath.ifPresent(s -> config = config.toBuilder().path(s).build());
        createTracer();

        // write a complete span so that it gets reported
        records.forEach(tracer::record);

        // block until the request arrived
        requests.add(server.takeRequest());
      }
      // we expect the first request to be sent to the default path, and the second to the overridden path
      assertThat(Objects.equals(requests.get(0).getPath(), path$.DEFAULT_PATH));
      assertThat(Objects.equals(requests.get(1).getPath(), customPath));
    } finally {
      server.shutdown();
    }
  }

  @Test public void compression() throws Exception {
    http.shutdown(); // shutdown the normal zipkin rule

    // create instructions to create a complete RPC span
    List<Record> records = asList(
        new Record(root, Time.fromMilliseconds(TODAY), new ServiceName("web"), none),
        new Record(root, Time.fromMilliseconds(TODAY), new Rpc("get"), none),
        new Record(root, Time.fromMilliseconds(TODAY), ClientSend$.MODULE$, none),
        new Record(root, Time.fromMilliseconds(TODAY + 1), ClientRecv$.MODULE$, none)
    );

    MockWebServer server = new MockWebServer();
    config = config.toBuilder().host("localhost:" + server.getPort()).build();
    try {
      List<RecordedRequest> requests = new ArrayList<>();
      for (boolean compressionEnabled : asList(true, false)) {
        // recreate the tracer with the compression configuration
        closeTracer();
        config = config.toBuilder().compressionEnabled(compressionEnabled).build();
        createTracer();

        // write a complete span so that it gets reported
        records.forEach(tracer::record);

        // block until the request arrived
        requests.add(server.takeRequest());
      }

      // we expect the first compressed request to be smaller than the uncompressed one.
      assertThat(requests.get(0).getBodySize())
          .isLessThan(requests.get(1).getBodySize());
    } finally {
      server.shutdown();
    }
  }
}
