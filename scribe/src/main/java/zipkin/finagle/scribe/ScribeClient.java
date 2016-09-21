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
package zipkin.finagle.scribe;

import com.twitter.finagle.Service;
import com.twitter.finagle.Thrift;
import com.twitter.finagle.context.Contexts;
import com.twitter.finagle.thrift.DeserializeCtx;
import com.twitter.finagle.thrift.ThriftClientRequest;
import com.twitter.util.AbstractClosable;
import com.twitter.util.Function;
import com.twitter.util.Function0;
import com.twitter.util.Future;
import com.twitter.util.Return;
import com.twitter.util.Throw;
import com.twitter.util.Time;
import com.twitter.util.Try;
import java.util.List;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TMemoryBuffer;
import org.apache.thrift.transport.TMemoryInputTransport;
import scala.runtime.BoxedUnit;
import zipkin.reporter.libthrift.InternalScribeCodec;

/**
 * This class's functional composition was derived from how scrooge does it. For example, scrooge
 * doesn't use thrift sequence ids.
 */
final class ScribeClient extends AbstractClosable {
  static final byte[] category = "zipkin".getBytes();

  private final Service<ThriftClientRequest, byte[]> service;

  ScribeClient(ScribeZipkinTracer.Config config) {
    service = Thrift.client().newService(config.host(), "zipkin-scribe");
  }

  static int messageSizeInBytes(List<byte[]> encodedSpans) {
    return InternalScribeCodec.messageSizeInBytes(category, encodedSpans);
  }

  Future<Boolean> sendMessage(List<byte[]> encodedSpans) throws TException {
    final ThriftClientRequest request = parseRequest(encodedSpans);
    DeserializeCtx ctx = new DeserializeCtx<>(request, READ_TRY);
    return Contexts.local().let(DeserializeCtx.Key(), ctx, new Function0<Future<Boolean>>() {
      @Override
      public Future<Boolean> apply() {
        Future<byte[]> done = service.apply(request);
        return done.flatMap(READ_FUTURE);
      }
    });
  }

  @Override public Future<BoxedUnit> close(Time time) {
    return service.close();
  }

  // Avoids needing to keep track of pooled buffers by allocating an exact size array.
  ThriftClientRequest parseRequest(List<byte[]> encodedSpans) throws TException {
    int encodedSize = InternalScribeCodec.messageSizeInBytes(category, encodedSpans);
    TMemoryBuffer mem = new TMemoryBuffer(encodedSize);
    TBinaryProtocol prot = new TBinaryProtocol(mem);
    InternalScribeCodec.writeLogRequest(category, encodedSpans, 0, prot);
    return new ThriftClientRequest(mem.getArray(), false);
  }

  static final Function<byte[], Try<Boolean>> READ_TRY =
      new Function<byte[], Try<Boolean>>() {
        public Try<Boolean> apply(byte[] responseBytes) {
          TBinaryProtocol iprot = new TBinaryProtocol(new TMemoryInputTransport(responseBytes));
          try {
            return new Return(InternalScribeCodec.readLogResponse(0, iprot));
          } catch (Exception e) {
            return new Throw(e);
          }
        }
      };

  static final Function<byte[], Future<Boolean>> READ_FUTURE =
      new Function<byte[], Future<Boolean>>() {
        public Future<Boolean> apply(byte[] responseBytes) {
          TBinaryProtocol iprot = new TBinaryProtocol(new TMemoryInputTransport(responseBytes));
          try {
            return Future.value(InternalScribeCodec.readLogResponse(0, iprot));
          } catch (Exception e) {
            return Future.exception(e);
          }
        }
      };
}
