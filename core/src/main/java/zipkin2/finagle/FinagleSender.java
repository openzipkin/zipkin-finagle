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

import com.twitter.finagle.Service;
import com.twitter.util.Await;
import com.twitter.util.Future;
import com.twitter.util.Try;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin2.Call;
import zipkin2.Callback;
import zipkin2.CheckResult;
import zipkin2.reporter.Sender;

/** Receives the Finagle generated traces and sends them off. */
public abstract class FinagleSender<C extends ZipkinTracer.Config, Req, Rep> extends Sender {
  final Service<Req, Rep> client;

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  protected FinagleSender(C config) {
    if (config == null) throw new NullPointerException("config == null");
    this.client = newClient(config);
    if (client == null) throw new NullPointerException("client == null");
  }

  protected abstract Service<Req, Rep> newClient(C config);

  @Override public Call<Void> sendSpans(List<byte[]> spans) {
    if (closeCalled) throw new IllegalStateException("closed");

    return new SendSpans(spans);
  }

  protected abstract Req makeRequest(List<byte[]> spans) throws Exception;

  /** sends an empty message to the configured host. */
  @Override public CheckResult check() {
    try {
      sendSpans(Collections.emptyList()).execute();
      return CheckResult.OK;
    } catch (Exception e) {
      return CheckResult.failed(e);
    }
  }

  @Override public void close() {
    closeFuture();
  }

  public Future<BoxedUnit> closeFuture() {
    if (closeCalled) return Future.Done();
    closeCalled = true;
    return client.close();
  }

  static final class WrappedException extends RuntimeException {
    WrappedException(Exception e) {
      super(e);
    }
  }

  class SendSpans extends Call.Base<Void> {
    final List<byte[]> spans;

    SendSpans(List<byte[]> spans) {
      this.spans = spans;
    }

    @Override protected Void doExecute() throws IOException {
      try {
        Await.result(client.apply(makeRequest(spans)));
      } catch (RuntimeException |IOException e) {
        throw e;
      } catch (Exception e) {
        throw new WrappedException(e);
      }
      return null;
    }

    @Override protected void doEnqueue(Callback<Void> callback) {
      try {
        client.apply(makeRequest(spans)).respond(new AbstractFunction1<Try<Rep>, BoxedUnit>() {
          @Override public BoxedUnit apply(Try<Rep> result) {
            if (result.isReturn()) {
              callback.onSuccess(null);
            } else {
              callback.onError(result.throwable());
            }
            return BoxedUnit.UNIT;
          }
        });
      } catch (Exception e) {
        callback.onError(e);
      }
    }

    @Override public Call<Void> clone() {
      return new SendSpans(spans);
    }
  }
}
