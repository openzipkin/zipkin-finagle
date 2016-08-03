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

import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.thrift.Protocols;
import com.twitter.finagle.zipkin.core.InternalZipkinTracer;
import com.twitter.finagle.zipkin.core.Span;
import com.twitter.util.Future;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TList;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.protocol.TType;
import org.apache.thrift.transport.TMemoryBuffer;
import scala.Function1;
import scala.collection.Iterator;
import scala.collection.Seq;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;

/**
 * Receives the Finagle generated traces and sends them off to Zipkin.
 *
 * <p>Implement this by defining your class and delegating to this. For example:
 * <pre>
 * &#064;AutoService(com.twitter.finagle.tracing.Tracer.class)
 * public final class HttpZipkinTracer extends SamplingTracer {
 *
 *  --snip--
 *
 *   // Default constructor for the service loader
 *   public HttpZipkinTracer() {
 *     this(Config.builder().build(),
 *          DefaultStatsReceiver$.MODULE$.get().scope("zipkin.http")
 *     );
 *   }
 *
 *   HttpZipkinTracer(Config config, StatsReceiver stats) {
 *     super(new HttpRawZipkinTracer(config.baseUrl(), stats),
 *           config.initialSampleRate()
 *     );
 *   }
 * }</pre>
 */
public abstract class RawZipkinTracer extends InternalZipkinTracer {

  /**
   * @param stats We generate stats to keep track of traces sent, failures and so on
   */
  protected RawZipkinTracer(StatsReceiver stats) {
    super(stats);
    this.protocolFactory = Protocols.binaryFactory(false, true, 0, stats);
    final Counter okCounter = stats.scope("log_span").counter0("ok");
    incrementOk = new AbstractFunction1<BoxedUnit, BoxedUnit>() {
      @Override public BoxedUnit apply(BoxedUnit v1) {
        okCounter.incr();
        return v1;
      }
    };
    final StatsReceiver errorReceiver = stats.scope("log_span").scope("error");
    incrementError = new AbstractFunction1<Throwable, BoxedUnit>() {
      @Override public BoxedUnit apply(Throwable e) {
        errorReceiver.counter0(e.getClass().getName()).incr();
        return BoxedUnit.UNIT;
      }
    };
  }

  final TProtocolFactory protocolFactory;
  final Function1<Throwable, BoxedUnit> incrementError;
  final Function1<BoxedUnit, BoxedUnit> incrementOk;

  @Override
  public Future<BoxedUnit> sendSpans(Seq<Span> spans) {
    try {
      byte[] encoded = spansToThriftByteArray(spans);
      return sendSpans(encoded)
          .onSuccess(incrementOk)
          .onFailure(incrementError);
    } catch (RuntimeException | TException e) {
      incrementError.apply(e);
      return Future.exception(e).unit();
    }
  }

  /**
   * Sends a thrift encoded list of spans over the current transport.
   */
  protected abstract Future<BoxedUnit> sendSpans(byte[] thrift);

  byte[] spansToThriftByteArray(Seq<Span> spans) throws TException {
    // serialize all spans as a thrift list
    TMemoryBuffer transport = new TMemoryBuffer(512); // bytes
    TProtocol protocol = protocolFactory.getProtocol(transport);
    protocol.writeListBegin(new TList(TType.STRUCT, spans.size()));
    for (Iterator<Span> i = spans.iterator(); i.hasNext(); ) {
      i.next().toThrift().write(protocol);
    }
    protocol.writeListEnd();
    return transport.getArray();
  }
}
