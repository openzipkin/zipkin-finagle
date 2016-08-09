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
package zipkin.finagle.http;

import com.twitter.finagle.Service;
import com.twitter.finagle.builder.ClientBuilder;
import com.twitter.finagle.http.Http;
import com.twitter.finagle.http.Methods;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.tracing.NullTracer;
import com.twitter.finagle.tracing.Trace;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Future;
import com.twitter.util.Time;
import com.twitter.util.Try;
import java.util.List;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.Codec;
import zipkin.Span;
import zipkin.storage.AsyncSpanConsumer;
import zipkin.storage.Callback;

/**
 * Receives the Finagle generated traces and sends them off to Zipkin via Http.
 *
 * <p>This uses json format, as it is more often used and easier for people to debug.
 */
final class HttpSpanConsumer extends AbstractClosable implements AsyncSpanConsumer {
  final Service<Request, Response> client;
  final String host;
  /**
   * @param host the zipkin host (also used as the http host header).
   */
  HttpSpanConsumer(String host) {
    this.client = ClientBuilder.safeBuild(ClientBuilder.get()
        .tracer(new NullTracer())
        .codec(Http.get().enableTracing(false))
        .hosts(host)
        .hostConnectionLimit(1));
    this.host = host;
  }

  @Override public void accept(final List<Span> spans, final Callback<Void> callback) {
    Trace.letClear(new AbstractFunction0<Void>() {
      @Override public Void apply() {
        try {
          sendSpans(spans, callback);
        } catch (RuntimeException e) {
          callback.onError(e);
        }
        return null;
      }
    });
  }

  void sendSpans(List<Span> spans, final Callback<Void> callback) {
    byte[] json = Codec.JSON.writeSpans(spans);
    Request request = Request.apply(Methods.POST, "/api/v1/spans");
    request.headerMap().add("Host", host);
    request.headerMap().add("Content-Type", "application/json");
    request.headerMap().add("Content-Length", String.valueOf(json.length));
    request.write(json);
    client.apply(request).respond(new AbstractFunction1<Try<Response>, BoxedUnit>() {
      @Override public BoxedUnit apply(Try<Response> result) {
        if (result.isReturn()) {
          callback.onSuccess(null);
        } else {
          callback.onError(result.throwable());
        }
        return null;
      }
    });
  }

  @Override public Future<BoxedUnit> close(Time deadline) {
    return client.close();
  }
}
