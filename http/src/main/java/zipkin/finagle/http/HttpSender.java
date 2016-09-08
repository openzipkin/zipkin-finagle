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

import com.twitter.finagle.Http;
import com.twitter.finagle.Http$;
import com.twitter.finagle.Service;
import com.twitter.finagle.ServiceFactory;
import com.twitter.finagle.Stack;
import com.twitter.finagle.http.Methods;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.tracing.NullTracer;
import com.twitter.finagle.tracing.Trace;
import com.twitter.util.Try;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import scala.runtime.AbstractFunction0;
import scala.runtime.AbstractFunction1;
import scala.runtime.BoxedUnit;
import zipkin.reporter.BytesMessageEncoder;
import zipkin.reporter.Callback;
import zipkin.reporter.Encoding;
import zipkin.reporter.Sender;

/**
 * Receives the Finagle generated traces and sends them off to Zipkin via Http.
 */
final class HttpSender implements Sender {
  final Service<Request, Response> client;
  final HttpZipkinTracer.Config config;

  HttpSender(HttpZipkinTracer.Config config) {
    // use special knowledge to yank out the trace filter since we are literally sending to zipkin
    // https://groups.google.com/forum/#!topic/finaglers/LqVVVOr2EMM
    Stack<ServiceFactory<Request, Response>> stack =
        Http$.MODULE$.client().stack().remove(new Stack.Role("TraceInitializerFilter"));
    this.client = new Http.Client(stack, Http.Client$.MODULE$.apply$default$2())
        .withTracer(new NullTracer())
        .newService(config.host(), "zipkin-http");
    this.config = config;
  }

  @Override public int messageMaxBytes() {
    return 5 * 1024 * 1024; // TODO: enforce
  }

  @Override public int messageSizeInBytes(List<byte[]> list) {
    return Encoding.THRIFT.listSizeInBytes(list);
  }

  @Override public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override public void sendSpans(final List<byte[]> spans, final Callback callback) {
    Trace.letClear(new AbstractFunction0<Void>() {
      @Override public Void apply() {
        try {
          doSendSpans(spans, callback);
        } catch (RuntimeException e) {
          callback.onError(e);
        }
        return null;
      }
    });
  }

  void doSendSpans(List<byte[]> spans, final Callback callback) {
    byte[] thrift = BytesMessageEncoder.THRIFT.encode(spans);
    Request request = Request.apply(Methods.POST, "/api/v1/spans");
    request.headerMap().add("Host", config.hostHeader());
    request.headerMap().add("Content-Type", "application/x-thrift");
    // Eventhough finagle compression flag exists, it only works for servers!
    if (config.compressionEnabled()) {
      request.headerMap().add("Content-Encoding", "gzip");
      ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
      try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
        compressor.write(thrift);
      } catch (IOException e) {
        callback.onError(e);
        return;
      }
      thrift = gzipped.toByteArray();
    }
    request.headerMap().add("Content-Length", String.valueOf(thrift.length));
    request.write(thrift);
    client.apply(request).respond(new AbstractFunction1<Try<Response>, BoxedUnit>() {
      @Override public BoxedUnit apply(Try<Response> result) {
        if (result.isReturn()) {
          callback.onComplete();
        } else {
          callback.onError(result.throwable());
        }
        return null;
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
