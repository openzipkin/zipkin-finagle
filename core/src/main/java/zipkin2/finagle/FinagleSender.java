/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import com.twitter.finagle.Service;
import com.twitter.util.Await;
import com.twitter.util.Future;
import com.twitter.util.Try;
import java.io.IOException;
import java.util.List;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin2.reporter.Callback;
import zipkin2.reporter.Call;
import zipkin2.reporter.BytesMessageSender;
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
