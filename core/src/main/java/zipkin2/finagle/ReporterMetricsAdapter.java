/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.StatsReceivers;
import com.twitter.util.Throwables;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import scala.collection.Seq;
import zipkin2.reporter.ReporterMetrics;

public final class ReporterMetricsAdapter implements ReporterMetrics {
  public static ReporterMetrics create(StatsReceiver stats) {
    return new ReporterMetricsAdapter(stats);
  }

  final Counter spans;
  final Counter spansDropped;
  final Counter spanBytes;
  final Counter messages;
  final Counter messageBytes;
  final StatsReceiver messagesDropped;
  final AtomicLong spanQueueSize;
  final AtomicLong spanQueueBytes;

  ReporterMetricsAdapter(StatsReceiver stats) {
    if (stats == null) throw new NullPointerException("stats == null");
    this.spans = stats.counter0("spans");
    this.spanBytes = stats.counter0("span_bytes");
    this.spansDropped = stats.counter0("spans_dropped");
    this.messages = stats.counter0("messages");
    this.messageBytes = stats.counter0("message_bytes");
    this.messagesDropped = stats.scope("messages_dropped");
    this.spanQueueSize = gaugeFor(stats, "span_queue_size");
    this.spanQueueBytes = gaugeFor(stats, "span_queue_bytes");
  }

  @Override public void incrementMessages() {
    messages.incr();
  }

  @Override public void incrementMessagesDropped(Throwable cause) {
    if (cause instanceof FinagleSender.WrappedException) cause = cause.getCause();
    Seq<String> causes = Throwables.mkString(cause);

    // Manually implement inits() as Traversable was replaced with Iterable in 2.13
    messagesDropped.counter().incr();
    int causeCount = causes.size();
    for (int i = 1; i <= causeCount; i++) {
      messagesDropped.counter(causes.slice(0, i).toSeq()).incr();
    }
  }

  @Override public void incrementSpans(int i) {
    spans.incr(i);
  }

  @Override public void incrementSpanBytes(int i) {
    spanBytes.incr(i);
  }

  @Override public void incrementMessageBytes(int i) {
    messageBytes.incr(i);
  }

  @Override public void incrementSpansDropped(int i) {
    spansDropped.incr(i);
  }

  @Override public void updateQueuedSpans(int i) {
    spanQueueSize.set(i);
  }

  @Override public void updateQueuedBytes(int i) {
    spanQueueBytes.set(i);
  }

  static AtomicLong gaugeFor(StatsReceiver stats, String scope) {
    final AtomicLong result = new AtomicLong();
    StatsReceivers.addGauge(stats, new Callable<Float>() {
      @Override public Float call() throws Exception {
        return result.floatValue();
      }
    }, scope);
    return result;
  }
}
