/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import com.twitter.finagle.service.TimeoutFilter;
import com.twitter.finagle.tracing.TraceId;
import com.twitter.util.Time;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import zipkin2.Endpoint;
import zipkin2.v1.V1Annotation;
import zipkin2.v1.V1BinaryAnnotation;
import zipkin2.v1.V1Span;

import static com.twitter.finagle.thrift.thrift.Constants.CLIENT_RECV;
import static com.twitter.finagle.thrift.thrift.Constants.SERVER_SEND;

final class MutableSpan {
  private final V1Span.Builder span;
  private final Time started;
  private final List<V1Annotation> annotations = new ArrayList<>();
  private final List<V1BinaryAnnotation> binaryAnnotations = new ArrayList<>();
  private boolean isComplete;
  private String service = "unknown";
  private Endpoint endpoint = Endpoints.UNKNOWN;

  MutableSpan(TraceId traceId, Time started) {
    this.span = V1Span.newBuilder();
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
        span.duration(timestamp.inMicroseconds() - annotations.get(0).timestamp());
      }
      isComplete = true;
    }

    annotations.add(V1Annotation.create(timestamp.inMicroseconds(), value, endpoint));
    return this;
  }

  synchronized MutableSpan addBinaryAnnotation(String key, String value) {
    binaryAnnotations.add(V1BinaryAnnotation.createString(key, value, endpoint));
    return this;
  }

  /**
   * Sets the endpoint in the span for any future annotations. Also sets the endpoint in any
   * previous annotations that lack one.
   */
  synchronized MutableSpan setEndpoint(Endpoint endpoint) {
    for (int i = 0; i < annotations.size(); i++) {
      V1Annotation a = annotations.get(i);
      if (a.endpoint().equals(Endpoints.UNKNOWN)) {
        annotations.set(i, V1Annotation.create(a.timestamp(), a.value(), endpoint));
      }
    }
    this.endpoint = endpoint;
    return this;
  }

  synchronized MutableSpan setAddress(String key, InetSocketAddress ia) {
    binaryAnnotations.add(V1BinaryAnnotation.createAddress(key, Endpoints.fromSocketAddress(ia)));
    return this;
  }

  synchronized String getService() {
    return service;
  }

  synchronized boolean isComplete() {
    return isComplete;
  }

  synchronized V1Span toSpan() {
    // fill in the host/service data for all the annotations
    for (V1Annotation a : annotations) {
      Endpoint ep = Endpoints.boundEndpoint(a.endpoint());
      span.addAnnotation(a.timestamp(), a.value(), ep.toBuilder().serviceName(service).build());
    }
    for (V1BinaryAnnotation ann : binaryAnnotations) {
      Endpoint ep = Endpoints.boundEndpoint(ann.endpoint());
      if (ann.stringValue() == null) { // service annotation
        span.addBinaryAnnotation(ann.key(), ep);
      } else {
        span.addBinaryAnnotation(
            ann.key(), ann.stringValue(), ep.toBuilder().serviceName(service).build());
      }
    }
    return span.build();
  }
}
