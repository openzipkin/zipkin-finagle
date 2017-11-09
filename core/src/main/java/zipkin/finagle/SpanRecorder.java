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

import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.Annotation;
import com.twitter.finagle.tracing.Record;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Duration;
import com.twitter.util.Future;
import com.twitter.util.Time;
import com.twitter.util.Time$;
import com.twitter.util.Timer;
import com.twitter.util.TimerTask;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import scala.Function0;
import scala.Function1;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.BinaryAnnotation;
import zipkin.Span;
import zipkin.reporter.Reporter;

final class SpanRecorder extends AbstractClosable {
  private static final Charset UTF_8 = Charset.forName("UTF-8");
  private static final byte[] TRUE = {1};
  private static final byte[] FALSE = {0};
  private static final String ERROR_FORMAT = "%s: %s"; // annotation: errorMessage
  final Duration ttl = Duration.fromSeconds(120);
  private final ConcurrentHashMap<TraceId, MutableSpan> spanMap = new ConcurrentHashMap<>(64);
  private final Reporter<Span> reporter;
  /**
   * Incrementing a counter instead of throwing allows finagle to add new event types ahead of
   * upgrading the zipkin tracer
   */
  private final StatsReceiver unhandledReceiver;
  private final TimerTask flusher;

  SpanRecorder(Reporter<Span> reporter, StatsReceiver stats, Timer timer) {
    this.reporter = reporter;
    this.unhandledReceiver = stats.scope("record").scope("unhandled");
    Function0<BoxedUnit> f = new AbstractFunction0<BoxedUnit>() {
      public BoxedUnit apply() {
        flush(ttl.ago());
        return null;      }
    };
    this.flusher = timer.schedule(ttl.$div(2L), f);
  }

  /**
   * Mutates the Span with whatever new info we have. If we see an "end" annotation we remove the
   * span and send it off.
   */
  void record(Record record) {
    MutableSpan span = spanMap.get(record.traceId());
    if (span == null) {
      MutableSpan newSpan = new MutableSpan(record.traceId(), Time$.MODULE$.now());
      MutableSpan prev = spanMap.putIfAbsent(record.traceId(), newSpan);
      span = prev != null ? prev : newSpan;
    }

    append(record, span);

    if (span.isComplete()) {
      spanMap.remove(record.traceId(), span);
      reporter.report(span.toSpan());
    }
  }

  /**
   * ported from {@link com.twitter.finagle.zipkin.core.RawZipkinTracer#record(Record)}
   */
  void append(Record record, MutableSpan span) {
    Annotation annotation = record.annotation();
    if (Annotation.WireSend$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "ws");
    } else if (Annotation.WireRecv$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "wr");
    } else if ((annotation instanceof Annotation.WireRecvError)) {
      String error = ((Annotation.WireRecvError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Wire Receive Error", error));
    } else if ((annotation instanceof Annotation.ClientSend)) {
      span.addAnnotation(record.timestamp(), "cs");
    } else if ((annotation instanceof Annotation.ClientRecv)) {
      span.addAnnotation(record.timestamp(), "cr");
    } else if ((annotation instanceof Annotation.ClientRecvError)) {
      String error = ((Annotation.ClientRecvError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Client Receive Error", error));
    } else if ((annotation instanceof Annotation.ServerSend)) {
      span.addAnnotation(record.timestamp(), "ss");
    } else if ((annotation instanceof Annotation.ServerRecv)) {
      span.addAnnotation(record.timestamp(), "sr");
    } else if ((annotation instanceof Annotation.ServerSendError)) {
      String error = ((Annotation.ServerSendError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Server Send Error", error));
    } else if ((annotation instanceof Annotation.ClientSendFragment)) {
      span.addAnnotation(record.timestamp(), "csf");
    } else if ((annotation instanceof Annotation.ClientRecvFragment)) {
      span.addAnnotation(record.timestamp(), "crf");
    } else if ((annotation instanceof Annotation.ServerSendFragment)) {
      span.addAnnotation(record.timestamp(), "ssf");
    } else if ((annotation instanceof Annotation.ServerRecvFragment)) {
      span.addAnnotation(record.timestamp(), "srf");
    } else if ((annotation instanceof Annotation.Message)) {
      String value = ((Annotation.Message) annotation).content();
      span.addAnnotation(record.timestamp(), value);
    } else if ((annotation instanceof Annotation.Rpc)) {
      String name = ((Annotation.Rpc) annotation).name();
      span.setName(name);
    } else if ((annotation instanceof Annotation.ServiceName)) {
      String service = ((Annotation.ServiceName) annotation).service();
      span.setServiceName(service);
    } else if ((annotation instanceof Annotation.BinaryAnnotation)) {
      String key = ((Annotation.BinaryAnnotation) annotation).key();
      Object value = ((Annotation.BinaryAnnotation) annotation).value();
      if ((value instanceof Boolean)) {
        byte[] val = ((Boolean) value) ? TRUE : FALSE;
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.BOOL);
      } else if ((value instanceof byte[])) {
        span.addBinaryAnnotation(key, (byte[]) value, BinaryAnnotation.Type.BYTES);
      } else if ((value instanceof ByteBuffer)) {
        byte[] val = ((ByteBuffer) value).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.BYTES);
      } else if ((value instanceof Short)) {
        byte[] val = ByteBuffer.allocate(2).putShort(0, (Short) value).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.I16);
      } else if ((value instanceof Integer)) {
        byte[] val = ByteBuffer.allocate(4).putInt(0, (Integer) value).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.I32);
      } else if ((value instanceof Long)) {
        byte[] val = ByteBuffer.allocate(8).putLong(0, (Long) value).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.I64);
      } else if ((value instanceof Double)) {
        byte[] val = ByteBuffer.allocate(8).putDouble(0, (Double) value).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.DOUBLE);
      } else if ((value instanceof String)) {
        byte[] val = ByteBuffer.wrap(((String) value).getBytes(UTF_8)).array();
        span.addBinaryAnnotation(key, val, BinaryAnnotation.Type.STRING);
      } else {
        unhandledReceiver.counter0(value.getClass().getName()).incr();
      }
    } else if ((annotation instanceof Annotation.LocalAddr)) {
      InetSocketAddress ia = ((Annotation.LocalAddr) annotation).ia();
      span.setEndpoint(Endpoints.boundEndpoint(Endpoints.fromSocketAddress(ia)));
    } else if ((annotation instanceof Annotation.ClientAddr)) {
      // use a binary annotation over a regular annotation to avoid a misleading timestamp
      InetSocketAddress ia = ((Annotation.ClientAddr) annotation).ia();
      span.setAddress("ca", ia);
    } else if ((annotation instanceof Annotation.ServerAddr)) {
      InetSocketAddress ia = ((Annotation.ServerAddr) annotation).ia();
      span.setAddress("sa", ia);
    } else {
      unhandledReceiver.counter0(annotation.getClass().getName()).incr();
    }
  }

  /** This sends off spans after the deadline is hit, no matter if it ended naturally or not. */
  void flush(Time deadline) {
    for (Iterator<MutableSpan> i = spanMap.values().iterator(); i.hasNext(); ) {
      MutableSpan span = i.next();
      if (span.started().$less$eq(deadline)) {
        i.remove();
        span.addAnnotation(deadline, "finagle.flush");
        reporter.report(span.toSpan());
      }
    }
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    return flusher.close(deadline);
  }
}
