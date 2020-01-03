/*
 * Copyright 2016-2018 The OpenZipkin Authors
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

import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.StatsReceivers;
import com.twitter.util.Throwables;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;
import scala.collection.Iterator;
import scala.collection.Iterable;
import scala.collection.immutable.Seq;
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
    Seq<String> a = Throwables.mkString(cause);
    Iterator<Iterable> i = a.inits();
    //    Seq<Iterable<String>> paths = Throwables.mkString(cause).inits().toSeq();
    //    for (Iterator<Seq<String>> i = paths.iterator(); i.hasNext();) {
    for (; i.hasNext();) {
      messagesDropped.counter(i.next().toSeq()).incr();
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
