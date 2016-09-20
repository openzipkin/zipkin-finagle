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

import com.google.common.util.concurrent.AtomicDouble;
import com.twitter.finagle.stats.Counter;
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.stats.StatsReceivers;
import com.twitter.util.Throwables;
import java.util.concurrent.Callable;
import scala.collection.Iterator;
import scala.collection.Seq;
import scala.collection.Traversable;
import zipkin.reporter.ReporterMetrics;

final class ReporterMetricsAdapter implements ReporterMetrics {

  final Counter spans;
  final Counter spansDropped;
  final Counter spanBytes;
  final Counter messages;
  final Counter messageBytes;
  final StatsReceiver messagesDropped;
  final AtomicDouble spanQueueSize;
  final AtomicDouble spanQueueBytes;

  ReporterMetricsAdapter(StatsReceiver stats) {
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
    Seq<Traversable<String>> paths = Throwables.mkString(cause).inits().toSeq();
    for (Iterator<Traversable<String>> i = paths.iterator(); i.hasNext();) {
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

  static AtomicDouble gaugeFor(StatsReceiver stats, String scope) {
    final AtomicDouble result = new AtomicDouble();
    StatsReceivers.addGauge(stats, new Callable<Float>() {
      @Override public Float call() throws Exception {
        return result.floatValue();
      }
    }, scope);
    return result;
  }
}
