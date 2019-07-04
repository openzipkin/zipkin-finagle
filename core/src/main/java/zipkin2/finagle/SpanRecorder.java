/*
 * Copyright 2016-2019 The OpenZipkin Authors
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
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import scala.runtime.BoxedUnit;
import zipkin2.Span;
import zipkin2.reporter.Reporter;
import zipkin2.v1.V1SpanConverter;

final class SpanRecorder extends AbstractClosable {
  private static final String ERROR_FORMAT = "%s: %s"; // annotation: errorMessage
  final Duration ttl = Duration.apply(120, TimeUnit.SECONDS);
  private final ConcurrentHashMap<TraceId, MutableSpan> spanMap = new ConcurrentHashMap<>(64);
  private final Reporter<zipkin2.Span> reporter;
  /**
   * Incrementing a counter instead of throwing allows finagle to add new event types ahead of
   * upgrading the zipkin tracer
   */
  private final StatsReceiver unhandledReceiver;
  private final TimerTask flusher;
  private final String localServiceName;

  SpanRecorder(Reporter<Span> reporter, StatsReceiver stats, Timer timer, String localServiceName) {
    this.reporter = reporter;
    this.unhandledReceiver = stats.scope("record").scope("unhandled");
    this.flusher = timer.schedule(ttl.$div(2L), () -> {
      flush(ttl.ago());
      return null;
    });
    this.localServiceName = "unknown".equals(localServiceName) ? null : localServiceName;
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
      report(span);
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
    } else if (annotation instanceof Annotation.WireRecvError) {
      String error = ((Annotation.WireRecvError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Wire Receive Error", error));
    } else if (Annotation.ClientSend$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "cs");
    } else if (Annotation.ClientRecv$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "cr");
    } else if (annotation instanceof Annotation.ClientRecvError) {
      String error = ((Annotation.ClientRecvError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Client Receive Error", error));
    } else if (Annotation.ServerSend$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "ss");
    } else if (Annotation.ServerRecv$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "sr");
    } else if (annotation instanceof Annotation.ServerSendError) {
      String error = ((Annotation.ServerSendError) annotation).error();
      span.addAnnotation(record.timestamp(),
          String.format(ERROR_FORMAT, "Server Send Error", error));
    } else if (Annotation.ClientSendFragment$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "csf");
    } else if (Annotation.ClientRecvFragment$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "crf");
    } else if (Annotation.ServerSendFragment$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "ssf");
    } else if (Annotation.ServerRecvFragment$.MODULE$.equals(annotation)) {
      span.addAnnotation(record.timestamp(), "srf");
    } else if (annotation instanceof Annotation.Message) {
      String value = ((Annotation.Message) annotation).content();
      span.addAnnotation(record.timestamp(), value);
    } else if (annotation instanceof Annotation.Rpc) {
      String name = ((Annotation.Rpc) annotation).name();
      span.setName(name);
    } else if (annotation instanceof Annotation.ServiceName) {
      String service = ((Annotation.ServiceName) annotation).service();
      span.setServiceName(service);
    } else if (annotation instanceof Annotation.BinaryAnnotation) {
      String key = ((Annotation.BinaryAnnotation) annotation).key();
      Object value = ((Annotation.BinaryAnnotation) annotation).value();
      if (value instanceof Boolean) {
        span.addBinaryAnnotation(key, value.toString());
      } else if (value instanceof Short) {
        span.addBinaryAnnotation(key, value.toString());
      } else if (value instanceof Integer) {
        span.addBinaryAnnotation(key, value.toString());
      } else if (value instanceof Long) {
        span.addBinaryAnnotation(key, value.toString());
      } else if (value instanceof Double) {
        span.addBinaryAnnotation(key, value.toString());
      } else if (value instanceof String) {
        span.addBinaryAnnotation(key, value.toString());
      } else {
        unhandledReceiver.counter0(value.getClass().getName()).incr();
      }
    } else if (annotation instanceof Annotation.LocalAddr) {
      InetSocketAddress ia = ((Annotation.LocalAddr) annotation).ia();
      span.setEndpoint(Endpoints.boundEndpoint(Endpoints.fromSocketAddress(ia)));
    } else if (annotation instanceof Annotation.ClientAddr) {
      // use a binary annotation over a regular annotation to avoid a misleading timestamp
      InetSocketAddress ia = ((Annotation.ClientAddr) annotation).ia();
      span.setAddress("ca", ia);
    } else if (annotation instanceof Annotation.ServerAddr) {
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
        report(span);
      }
    }
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    return flusher.close(deadline);
  }

  void report(MutableSpan span) {
    // Override the local service name
    if (localServiceName != null) span.setServiceName(localServiceName);
    for (zipkin2.Span v2span : V1SpanConverter.create().convert(span.toSpan())) {
      reporter.report(v2span);
    }
  }
}
