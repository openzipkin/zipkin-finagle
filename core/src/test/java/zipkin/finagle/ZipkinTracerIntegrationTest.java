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
  }

  @Before
  public void createTracer() {
    tracer = newTracer();
  }

  protected abstract ZipkinTracer newTracer();

  protected abstract List<List<Span>> getTraces() throws Exception;

  @Test public void multipleSpansGoIntoSameMessage() throws Exception {
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(root, fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    tracer.record(new Record(child, fromMilliseconds(TODAY), new ServiceName("web"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), new Rpc("get"), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY), new ClientSend(), empty()));
    tracer.record(new Record(child, fromMilliseconds(TODAY + 1), new ClientRecv(), empty()));

    Thread.sleep(2000); // the AsyncReporter thread has a default interval of 1s

    assertThat(mapAsJavaMap(stats.counters())).containsExactly(
        entry(seq("span_bytes"), 341),
        entry(seq("spans"), 2),
        entry(seq("message_bytes"), 346),
        entry(seq("messages"), 1)
    );
  }
}
