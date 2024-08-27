/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle.scribe;

import com.twitter.finagle.Name;
import com.twitter.finagle.Service;
import com.twitter.finagle.Status;
import com.twitter.finagle.Thrift;
import com.twitter.finagle.thrift.ThriftClientRequest;
import com.twitter.finagle.tracing.NullTracer;
import com.twitter.util.Duration;
import com.twitter.util.Function;
import com.twitter.util.Future;
import com.twitter.util.Time;
import java.util.List;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;
import scala.runtime.BoxedUnit;
import zipkin2.reporter.Encoding;
import zipkin2.finagle.FinagleSender;
import zipkin2.finagle.scribe.ScribeZipkinTracer.Config;
import zipkin2.reporter.libthrift.InternalScribeCodec;

/** Receives the Finagle generated traces and sends them off to Zipkin via Scribe. */
final class ScribeSender extends FinagleSender<Config, ThriftClientRequest, Void> {
  static final byte[] category = new byte[] {'z', 'i', 'p', 'k', 'i', 'n'};

  final Name host;

  ScribeSender(Config config) {
    super(config);
    this.host = config.host();
  }

  @Override public int messageSizeInBytes(int encodedSizeInBytes) {
    return InternalScribeCodec.messageSizeInBytes(category, encodedSizeInBytes);
  }

  @Override public int messageSizeInBytes(List<byte[]> spans) {
    return InternalScribeCodec.messageSizeInBytes(category, spans);
  }

  @Override public Encoding encoding() {
    return Encoding.THRIFT;
  }

  @Override public int messageMaxBytes() {
    return 1 * 1024 * 1024; // previous default
  }

  @Override
  protected Service<ThriftClientRequest, Void> newClient(Config config) {
    return new ScribeClient(config);
  }

  /** This doesn't use thrift sequence ids because scrooge doesn't */
  @Override protected ThriftClientRequest makeRequest(List<byte[]> spans) throws TException {
    int encodedSize = InternalScribeCodec.messageSizeInBytes(category, spans);
    TMemoryBuffer mem = new TMemoryBuffer(encodedSize);
    TBinaryProtocol prot = new TBinaryProtocol(mem);
    InternalScribeCodec.writeLogRequest(category, spans, 0, prot);
    return new ThriftClientRequest(mem.getArray(), false);
  }

  @Override
  public final String toString() {
    return "ScribeSender(" + host + ")";
  }

  static final class ScribeClient extends Service<ThriftClientRequest, Void> {

    final Service<ThriftClientRequest, byte[]> delegate;

    ScribeClient(Config config) {
      delegate = Thrift.client()
          .withTracer(new NullTracer())
          .newService(config.host(), "zipkin-scribe");
    }

    @Override public Future<Void> apply(ThriftClientRequest request) {
      return delegate.apply(request).flatMap(READ_FUTURE);
    }

    @Override public Future<BoxedUnit> close(Duration after) {
      return delegate.close(after);
    }

    @Override public Future<BoxedUnit> close(Time deadline) {
      return delegate.close(deadline);
    }

    @Override public Status status() {
      return delegate.status();
    }

    @Override public String toString() {
      return delegate.toString();
    }
  }

  static final Function<byte[], Future<Void>> READ_FUTURE = new Function<byte[], Future<Void>>() {
    @Override public Future<Void> apply(byte[] responseBytes) {
      TBinaryProtocol iprot = new TBinaryProtocol(new TMemoryInputTransport(responseBytes));
      try {
        if (InternalScribeCodec.readLogResponse(0, iprot)) {
          return Future.Void();
        } else {
          return Future.exception(new IllegalStateException("try later"));
        }
      } catch (Exception e) {
        return Future.exception(e);
      }
    }
  };
}
