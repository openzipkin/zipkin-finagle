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
package zipkin.finagle.scribe;

import com.twitter.finagle.tracing.Trace;
import com.twitter.util.Try;
import java.util.List;
import org.apache.thrift.TException;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;

/**
 * Receives the Finagle generated traces and sends them off to Zipkin via Scribe.
 */
final class ScribeSender implements Sender {
  final ScribeClient client;
  final ScribeZipkinTracer.Config config;

  ScribeSender(ScribeZipkinTracer.Config config) {
    this.client = new ScribeClient(config);
    this.config = config;
  }

  @Override public int messageMaxBytes() {
    return 5 * 1024 * 1024;
  }

  @Override public int messageSizeInBytes(List<byte[]> encodedSpans) {
    return ScribeClient.messageSizeInBytes(encodedSpans);
  }

  @Override public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override public void sendSpans(final List<byte[]> spans, final Callback callback) {
    Trace.letClear(new AbstractFunction0<Void>() {
      @Override public Void apply() {
        try {
          doSendSpans(spans, callback);
        } catch (TException | RuntimeException e) {
          callback.onError(e);
        }
        return null;
      }
    });
  }

  void doSendSpans(List<byte[]> spans, final Callback callback) throws TException {
    client.sendMessage(spans).respond(new AbstractFunction1<Try<Boolean>, BoxedUnit>() {
      @Override public BoxedUnit apply(Try<Boolean> result) {
        if (result.isReturn()) {
          if (result.get()) {
            callback.onComplete();
          } else {
            callback.onError(new IllegalStateException("try later"));
          }
        } else {
          callback.onError(result.throwable());
        }
        return BoxedUnit.UNIT;
      }
    });
  }

  @Override public CheckResult check() {
    return CheckResult.OK; // TODO
  }

  @Override public void close() {
    client.close();
  }
}
