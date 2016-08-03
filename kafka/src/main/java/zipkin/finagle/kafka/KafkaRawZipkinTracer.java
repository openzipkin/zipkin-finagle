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
package zipkin.finagle.kafka;

import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.util.Future;
import com.twitter.util.Promise;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import scala.runtime.BoxedUnit;
import zipkin.finagle.RawZipkinTracer;

/** Receives the Finagle generated traces and sends them off to Zipkin via Kafka. */
final class KafkaRawZipkinTracer extends RawZipkinTracer {

  /**
   * @param producer kafka producer
   * @param topic kafka topic to send to
   * @param stats We generate stats to keep track of traces sent, failures and so on
   */
  KafkaRawZipkinTracer(Producer<byte[], byte[]> producer, String topic, StatsReceiver stats) {
    super(stats);
    this.producer = producer;
    this.topic = topic;
  }
  // XXX(sveinnfannar): Should we override RawZipkinTracer#flush to make sure
  //                    the kafka producer sends all queued messages

  final Producer<byte[], byte[]> producer;
  final String topic;

  @Override protected Future<BoxedUnit> sendSpans(byte[] thrift) {
    final Promise<BoxedUnit> promise = Promise.apply();
    // Producer#send appends the message to an internal queue and returns immediately.
    // Messages are then batch sent asynchronously on another thread and the callback invoked.
    producer.send(new ProducerRecord(topic, thrift), new Callback() {
      @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
          promise.setValue(BoxedUnit.UNIT);
        } else {
          promise.setException(exception);
        }
      }
    });
    return promise;
  }
}
