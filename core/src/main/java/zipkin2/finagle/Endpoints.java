/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
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
