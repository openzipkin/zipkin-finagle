/*
 * Copyright 2016-2018 The OpenZipkin Authors
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
package zipkin2.finagle.http;

import com.twitter.finagle.Http;
import com.twitter.finagle.Http$;
import com.twitter.finagle.Service;
import com.twitter.finagle.ServiceFactory;
import com.twitter.finagle.Stack;
import com.twitter.finagle.http.Method;
import com.twitter.finagle.http.Request;
import com.twitter.finagle.http.Response;
import com.twitter.finagle.tracing.NullTracer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import zipkin2.codec.Encoding;
import zipkin2.finagle.FinagleSender;
import zipkin2.reporter.BytesMessageEncoder;

/** Receives the Finagle generated traces and sends them off to Zipkin via Http. */
final class HttpSender extends FinagleSender<HttpZipkinTracer.Config, Request, Response> {
  static final Method POST = Method.apply("POST");
  final HttpZipkinTracer.Config config;

  HttpSender(HttpZipkinTracer.Config config) {
    super(config);
    this.config = config;
  }

  @Override public Encoding encoding() {
    return Encoding.JSON;
  }

  @Override public int messageMaxBytes() {
    return 5 * 1024 * 1024; // TODO: enforce
  }

  @Override public int messageSizeInBytes(List<byte[]> list) {
    return encoding().listSizeInBytes(list);
  }

  @Override protected Service<Request, Response> newClient(HttpZipkinTracer.Config config) {
    // use special knowledge to yank out the trace filter since we are literally sending to zipkin
    // https://groups.google.com/forum/#!topic/finaglers/LqVVVOr2EMM
    Stack<ServiceFactory<Request, Response>> stack =
        Http$.MODULE$.client().stack().remove(new Stack.Role("TraceInitializerFilter"));
    return new Http.Client(stack, Http.Client$.MODULE$.apply$default$2())
        .withTracer(new NullTracer())
        .newService(config.host(), "zipkin-http");
  }

  @Override protected Request makeRequest(List<byte[]> spans) throws IOException {
    byte[] json = BytesMessageEncoder.JSON.encode(spans);
    Request request = Request.apply(POST, "/api/v2/spans");
    request.headerMap().add("Host", config.hostHeader());
    request.headerMap().add("Content-Type", "application/json");
    // Eventhough finagle compression flag exists, it only works for servers!
    if (config.compressionEnabled()) {
      request.headerMap().add("Content-Encoding", "gzip");
      ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
      try (GZIPOutputStream compressor = new GZIPOutputStream(gzipped)) {
        compressor.write(json);
      }
      json = gzipped.toByteArray();
    }
    request.headerMap().add("Content-Length", String.valueOf(json.length));
    request.write(json);
    return request;
  }

  @Override public final String toString() {
    return "HttpSender{" + config.host() + "}";
  }
}
