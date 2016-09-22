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

import com.twitter.finagle.Service;
import com.twitter.finagle.tracing.Trace;
import com.twitter.util.Future;
import com.twitter.util.Try;
import java.util.Collections;
import java.util.List;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;
import zipkin.reporter.internal.AwaitableCallback;

import static zipkin.internal.Util.checkNotNull;

/** Receives the Finagle generated traces and sends them off. */
public abstract class FinagleSender<C extends ZipkinTracer.Config, Req, Rep> implements Sender {
  final Service<Req, Rep> client;

  /** close is typically called from a different thread */
  transient boolean closeCalled;

  protected FinagleSender(C config) {
    this.client = checkNotNull(newClient(config), "client");
  }

  protected abstract Service<Req, Rep> newClient(C config);

  @Override public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override public void sendSpans(final List<byte[]> spans, final Callback callback) {
    Trace.letClear(new AbstractFunction0<Void>() {
      @Override public Void apply() {
        try {
          if (closeCalled) throw new IllegalStateException("closed");
          client.apply(makeRequest(spans)).respond(new AbstractFunction1<Try<Rep>, BoxedUnit>() {
            @Override public BoxedUnit apply(Try<Rep> result) {
              if (result.isReturn()) {
                callback.onComplete();
              } else {
                callback.onError(result.throwable());
              }
              return BoxedUnit.UNIT;
            }
          });
        } catch (Throwable e) {
          callback.onError(e);
          if (e instanceof Error) throw (Error) e;
        }
        return null;
      }
    });
  }

  protected abstract Req makeRequest(List<byte[]> spans) throws Exception;

  /** sends an empty message to the configured host. */
  @Override public CheckResult check() {
    AwaitableCallback callback = new AwaitableCallback();
    try {
      sendSpans(Collections.<byte[]>emptyList(), callback);
      callback.await();
      return CheckResult.OK;
    } catch (Exception e) {
      return CheckResult.failed(e);
    }
  }

  @Override public void close() {
    closeFuture();
  }

  public Future<BoxedUnit> closeFuture() {
    if (closeCalled) return null;
    closeCalled = true;
    return client.close();
  }
}
