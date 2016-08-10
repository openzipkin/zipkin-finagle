/**
 * Copyright 2016 The OpenZipkin Authors
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
package zipkin.finagle.http;

import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Duration;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import zipkin.Span;
import zipkin.finagle.ZipkinTracer;
import zipkin.finagle.ZipkinTracerTest;
import zipkin.finagle.http.HttpZipkinTracer.Config;
import zipkin.junit.ZipkinRule;

import static com.twitter.util.Time.fromMilliseconds;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.root;
import static zipkin.finagle.FinagleTestObjects.seq;

public class HttpZipkinTracerTest extends ZipkinTracerTest {
  final Option<Duration> none = Option.empty(); // avoid having to force generics
  @Rule
  public ZipkinRule http = new ZipkinRule();
  String host = "localhost:" + URI.create(http.httpUrl()).getPort();
  Config config = Config.builder().initialSampleRate(1.0f).host(host).build();

  @Override protected ZipkinTracer newTracer() {
    return new HttpZipkinTracer(config, stats);
  }

  @Override protected List<List<Span>> getTraces() {
    return http.getTraces();
  }

  @Test
  public void whenHttpIsDown() throws Exception {
    closeTracer();
    config = config.toBuilder().host("127.0.0.1:65535").build();
    createTracer();

    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), none));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), none));

    Thread.sleep(500); // wait for http request attempt to go through

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("log_span", "error", "com.twitter.finagle.ChannelWriteException"), 1)
    );
  }

  @Test public void compression() throws Exception {
    http.shutdown(); // shutdown the normal zipkin rule

    // create instructions to create a complete RPC span
    List<Record> records = asList(
        new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), none),
        new Record(root, fromMilliseconds(TODAY), new Rpc("get"), none),
        new Record(root, fromMilliseconds(TODAY), new ClientSend(), none),
        new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), none)
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
        records.stream().forEach(tracer::record);

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
