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

import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Await;
import com.twitter.util.Duration;
import java.net.URI;
import java.util.List;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import scala.Option;
import zipkin.Span;
import zipkin.finagle.RawZipkinTracer;
import zipkin.finagle.RawZipkinTracerTest;
import zipkin.finagle.http.HttpZipkinTracer.Config;
import zipkin.junit.ZipkinRule;

import static com.twitter.util.Time.fromMilliseconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.collection.JavaConversions.mapAsJavaMap;

public class HttpRawZipkinTracerTest extends RawZipkinTracerTest {

  @Rule
  public ZipkinRule http = new ZipkinRule();

  Config config =
      Config.builder().host("localhost:" + URI.create(http.httpUrl()).getPort()).build();

  @After
  public void closeClient() {
    ((HttpRawZipkinTracer) tracer).client.close();
  }

  @Override protected RawZipkinTracer newTracer(StatsReceiver stats) {
    return new HttpRawZipkinTracer(config.host(), stats);
  }

  @Override protected List<List<Span>> getTraces() {
    return http.getTraces();
  }

  @Test
  public void whenHttpIsDown() throws Exception {
    closeClient();
    config = Config.builder().host("127.0.0.1:65535").build();
    tracer = newTracer(stats);

    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ServiceName("web"), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new Rpc("get"), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY), new ClientSend(), none));
    tracer.record(new Record(traceId, fromMilliseconds(TODAY + 1), new ClientRecv(), none));

    Await.result(tracer.flush());

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("log_span", "error", "com.twitter.finagle.ChannelWriteException"), 1)
    );
  }

  final Option<Duration> none = Option.empty(); // avoid having to force generics
}
