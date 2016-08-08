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
package zipkin.finagle;

import com.twitter.finagle.stats.InMemoryStatsReceiver;
import com.twitter.finagle.tracing.Annotation.ClientRecv;
import com.twitter.finagle.tracing.Annotation.ClientSend;
import com.twitter.finagle.tracing.Annotation.Rpc;
import com.twitter.finagle.tracing.Annotation.ServiceName;
import com.twitter.finagle.tracing.Record;
import com.twitter.util.Await;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import zipkin.Span;

import static com.twitter.util.Time.fromMilliseconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static scala.Option.empty;
import static scala.collection.JavaConversions.mapAsJavaMap;
import static zipkin.finagle.FinagleTestObjects.TODAY;
import static zipkin.finagle.FinagleTestObjects.seq;
import static zipkin.finagle.FinagleTestObjects.root;

public abstract class ZipkinTracerTest {
  @Rule
  public ExpectedException thrown = ExpectedException.none();
  protected InMemoryStatsReceiver stats = new InMemoryStatsReceiver();
  protected ZipkinTracer tracer;

  @After
  public void closeTracer() throws Exception {
    Await.result(tracer.close());
  }

  @Before
  public void createTracer() {
    tracer = newTracer();
  }

  protected abstract ZipkinTracer newTracer();

  protected abstract List<List<Span>> getTraces() throws Exception;

  @Test public void unfinishedSpansArentImplicitlyFlushed() throws Exception {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), empty()));

    assertThat(getTraces()).isEmpty();
  }

  @Test public void finishedSpansAreImplicitlyFlushed() throws Exception {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), empty()));

    // client receive flushes the span
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    Thread.sleep(1000); // the flush is usually in the background, so no future to block on.

    assertThat(getTraces().stream().flatMap(List::stream))
        .flatExtracting(s -> s.annotations)
        .extracting(a -> a.value)
        .containsExactly("cs", "cr");
  }

  @Test
  public void reportIncrementsAcceptedMetrics() throws Exception {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    Thread.sleep(1000); // the flush is usually in the background, so no future to block on.

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("log_span", "ok"), 1)
    );
  }
}
