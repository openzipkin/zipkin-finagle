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
package zipkin.finagle;

import com.twitter.finagle.service.TimeoutFilter;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.util.Time;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import zipkin.Annotation;
import zipkin.BinaryAnnotation;
import zipkin.Endpoint;
import zipkin.Span;

import static com.twitter.finagle.thrift.thrift.Constants.CLIENT_RECV;
import static com.twitter.finagle.thrift.thrift.Constants.SERVER_SEND;

final class MutableSpan {
  private final Span.Builder span;
  private final Time started;
  private final List<Annotation> annotations = new ArrayList<>();
  private final List<BinaryAnnotation> binaryAnnotations = new ArrayList<>();
  private boolean isComplete = false;
  private String service = "unknown";
  private Endpoint endpoint = Endpoints.UNKNOWN;

  MutableSpan(TraceId traceId, Time started) {
    this.span = Span.builder();
    span.id(traceId.spanId().toLong());
    if (traceId._parentId().isDefined()) {
      span.parentId(traceId.parentId().toLong());
    }
    span.traceId(traceId.traceId().toLong());
    if (traceId.traceIdHigh().isDefined()) {
      span.traceIdHigh(traceId.traceIdHigh().get().toLong());
    }
    if (traceId.flags().isDebug()) {
      span.debug(true);
    }
    span.name("unknown");
    this.started = started;
  }

  Time started() { // not synchronized as the field is immutable and final
    return started;
  }

  synchronized MutableSpan setName(String n) {
    span.name(n);
    return this;
  }

  synchronized MutableSpan setServiceName(String n) {
    service = n;
    return this;
  }

  synchronized MutableSpan addAnnotation(Time timestamp, String value) {
    if (annotations.isEmpty()) {
      span.timestamp(timestamp.inMicroseconds());
    }

    if (!isComplete &&
        (value.equals(CLIENT_RECV) ||
         value.equals(SERVER_SEND) ||
         value.equals(TimeoutFilter.TimeoutAnnotation()))) {
      if (!annotations.isEmpty()) {
        span.duration(timestamp.inMicroseconds() - annotations.get(0).timestamp);
      }
      isComplete = true;
    }

    annotations.add(Annotation.create(timestamp.inMicroseconds(), value, endpoint));
    return this;
  }

  synchronized MutableSpan addBinaryAnnotation(String key, byte[] value,
      BinaryAnnotation.Type type) {
    binaryAnnotations.add(BinaryAnnotation.create(key, value, type, endpoint));
    return this;
  }

  /**
   * Sets the endpoint in the span for any future annotations. Also sets the endpoint in any
   * previous annotations that lack one.
   */
  synchronized MutableSpan setEndpoint(Endpoint endpoint) {
    for (int i = 0; i < annotations.size(); i++) {
      Annotation a = annotations.get(i);
      if (a.endpoint.equals(Endpoints.UNKNOWN)) {
        annotations.set(i, a.toBuilder().endpoint(endpoint).build());
      }
    }
    this.endpoint = endpoint;
    return this;
  }

  synchronized MutableSpan setAddress(String key, InetSocketAddress ia) {
    binaryAnnotations.add(BinaryAnnotation.address(key, Endpoints.fromSocketAddress(ia)));
    return this;
  }

  synchronized Span toSpan() {
    // fill in the host/service data for all the annotations
    for (Annotation ann : annotations) {
      Endpoint ep = Endpoints.boundEndpoint(ann.endpoint);
      span.addAnnotation(
          ann.toBuilder().endpoint(ep.toBuilder().serviceName(service).build()).build());
    }
    for (BinaryAnnotation ann : binaryAnnotations) {
      Endpoint ep = Endpoints.boundEndpoint(ann.endpoint);
      // TODO: service name for "ca" or "sa" is likely incorrect
      span.addBinaryAnnotation(
          ann.toBuilder().endpoint(ep.toBuilder().serviceName(service).build()).build());
    }
    return span.build();
  }

  synchronized boolean isComplete() {
    return isComplete;
  }
}
