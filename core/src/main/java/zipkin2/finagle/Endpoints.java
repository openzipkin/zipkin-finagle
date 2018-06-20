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
package zipkin2.finagle;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import zipkin2.Endpoint;

final class Endpoints {
  static final Endpoint LOOPBACK = Endpoint.newBuilder().ip("127.0.0.1").build();
  static final Endpoint UNKNOWN = Endpoint.newBuilder().build();
  static final Endpoint LOCAL;

  static {
    Endpoint.Builder builder = Endpoint.newBuilder();
    builder.parseIp(InetAddress.getLoopbackAddress());
    LOCAL = builder.build();
  }

  private Endpoints() {
  }

  /**
   * @return If possible, convert from a SocketAddress object to an Endpoint. If not, return Unknown
   * Endpoint.
   */
  static Endpoint fromSocketAddress(SocketAddress socketAddress) {
    if (socketAddress instanceof InetSocketAddress) {
      Endpoint.Builder builder = Endpoint.newBuilder();
      InetSocketAddress inet = (InetSocketAddress) socketAddress;
      builder.parseIp(inet.getAddress());
      builder.port(inet.getPort());
      return builder.build();
    }
    return UNKNOWN;
  }

  /**
   * @return If this endpoint's ip is 0.0.0.0 or 127.0.0.1 we get the local host and return that.
   */
  static Endpoint boundEndpoint(Endpoint endpoint) {
    if (endpoint.ipv4() == null || endpoint.ipv4().equals(LOOPBACK.ipv4())) {
      return LOCAL
          .toBuilder()
          .serviceName(endpoint.serviceName())
          .port(endpoint.portAsInt())
          .build();
    } else {
      return endpoint;
    }
  }
}
