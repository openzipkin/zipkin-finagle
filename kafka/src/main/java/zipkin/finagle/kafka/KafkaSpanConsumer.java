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

import com.twitter.util.AbstractClosable;
import com.twitter.util.Future;
import com.twitter.util.Time;
import java.util.List;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import scala.runtime.BoxedUnit;
import zipkin.Codec;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;

/** Receives the Finagle generated traces and sends them off to Zipkin via Kafka. */
final class KafkaSpanConsumer extends AbstractClosable implements AsyncSpanConsumer {

  final Producer<byte[], byte[]> producer;
  final String topic;
  /**
   * @param producer kafka producer
   * @param topic kafka topic to send to
   */
  KafkaSpanConsumer(Producer<byte[], byte[]> producer, String topic) {
    this.producer = producer;
    this.topic = topic;
  }

  @Override public void accept(List<Span> spans, final zipkin.storage.Callback<Void> callback) {
    byte[] thrift;
    try {
      thrift = Codec.THRIFT.writeSpans(spans);
    } catch (RuntimeException e) {
      callback.onError(e);
      throw e;
    }

    // NOTE: this blocks until the metadata server is available
    producer.send(new ProducerRecord(topic, thrift), new Callback() {
      @Override public void onCompletion(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
          callback.onSuccess(null);
        } else {
          callback.onError(exception);
        }
      }
    });
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    producer.close(); // TODO: this is blocking
    return Future.Done();
  }
}
