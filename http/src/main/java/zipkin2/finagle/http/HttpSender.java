/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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

import zipkin2.finagle.FinagleSender;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.Encoding;

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

  @Override public int messageSizeInBytes(int encodedSizeInBytes) {
    return encoding().listSizeInBytes(encodedSizeInBytes);
  }

  @Override public int messageSizeInBytes(List<byte[]> encodedSpans) {
    return encoding().listSizeInBytes(encodedSpans);
  }

  @Override protected Service<Request, Response> newClient(HttpZipkinTracer.Config config) {
    // use special knowledge to yank out the trace filter since we are literally sending to zipkin
    // https://groups.google.com/forum/#!topic/finaglers/LqVVVOr2EMM
    Stack<ServiceFactory<Request, Response>> stack =
        Http$.MODULE$.client().stack().remove(new Stack.Role("TraceInitializerFilter"));
    Http.Client client = new Http.Client(stack, Http.Client$.MODULE$.apply$default$2())
        .withTracer(new NullTracer());
    if (config.tlsEnabled()) {
      if (config.tlsValidationEnabled()) {
        client = client.withTls(config.hostHeader());
      } else {
        client = client.withTlsWithoutValidation();
      }
    }
    return client.newService(config.host(), "zipkin-http");
  }

  @Override protected Request makeRequest(List<byte[]> spans) throws IOException {
    byte[] json = BytesMessageEncoder.JSON.encode(spans);
    Request request = Request.apply(POST, config.path());
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
