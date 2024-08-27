/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.kafka;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;

public final class bootstrapServers$ extends JavaGlobalFlag<List<InetSocketAddress>> {
  private bootstrapServers$() {
    super(Collections.singletonList(new InetSocketAddress("localhost", 9092)),
        "Initial set of kafka servers to connect to, rest of cluster will be discovered (comma separated)",
        Flaggable.ofJavaList(Flaggable.ofInetSocketAddress()));
  }

  public static final Flag<List<InetSocketAddress>> Flag = new bootstrapServers$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
