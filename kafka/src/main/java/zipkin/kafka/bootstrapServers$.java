/*
 * Copyright 2016-2024 The OpenZipkin Authors
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
