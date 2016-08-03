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
import com.twitter.finagle.stats.StatsReceiver;
import com.twitter.finagle.tracing.NullTracer;
import com.twitter.finagle.tracing.Trace;
import com.twitter.util.Future;
import scala.runtime.AbstractFunction0;
import scala.runtime.BoxedUnit;
import zipkin.finagle.RawZipkinTracer;

/** Receives the Finagle generated traces and sends them off to Zipkin via Http. */
final class HttpRawZipkinTracer extends RawZipkinTracer {

  /**
   * @param host the zipkin host (also used as the http host header).
   * @param stats We generate stats to keep track of traces sent, failures and so on
   */
  HttpRawZipkinTracer(String host, StatsReceiver stats) {
    super(stats);
    this.client = ClientBuilder.safeBuild(ClientBuilder.get()
        .tracer(new NullTracer())
        .codec(Http.get().enableTracing(false))
        .hosts(host)
        .hostConnectionLimit(1));
    this.host = host;
  }

  final Service<Request, Response> client;
  final String host;

  @Override protected Future<BoxedUnit> sendSpans(final byte[] thrift) {
    return Trace.letClear(new AbstractFunction0<Future<BoxedUnit>>() {
      @Override public Future<BoxedUnit> apply() {
        Request request = Request.apply(Methods.POST, "/api/v1/spans");
        request.headerMap().add("Host", host);
        request.headerMap().add("Content-Type", "application/x-thrift");
        request.headerMap().add("Content-Length", String.valueOf(thrift.length));
        request.write(thrift);
        return client.apply(request).unit();
      }
    });
  }
}
