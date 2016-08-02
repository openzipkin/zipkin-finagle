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
package zipkin.finagle.kafka;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable$;
import com.twitter.app.GlobalFlag;
import com.twitter.finagle.zipkin.core.Sampler$;
import java.net.InetSocketAddress;
import java.util.Arrays;
import scala.collection.Iterator;
import scala.collection.Seq;
import scala.collection.mutable.StringBuilder;
import scala.runtime.BoxesRunTime;

import static scala.collection.JavaConversions.asScalaBuffer;

/**
 * This class contains a port of all the global flags
 */
public final class KafkaZipkinTracerFlags {

  static String bootstrapServers() {
    StringBuilder result = new StringBuilder();
    Iterator<InetSocketAddress> i = bootstrapServers$.MODULE$.apply().iterator();
    while (i.hasNext()) {
      InetSocketAddress next = i.next();
      result.append(next.getHostName()).append(':').append(next.getPort());
      if (i.hasNext()) result.append(',');
    }
    return result.toString();
  }

  public static final class bootstrapServers$ extends GlobalFlag<Seq<InetSocketAddress>> {
    public static final bootstrapServers$ MODULE$ = new bootstrapServers$();

    @Override public String name() {
      return "zipkin.kafka.bootstrapServers";
    }

    private bootstrapServers$() {
      super(asScalaBuffer(Arrays.asList(new InetSocketAddress("localhost", 9092))),
          "Initial set of kafka servers to connect to, rest of cluster will be discovered (comma separated)",
          Flaggable$.MODULE$.ofSeq(Flaggable$.MODULE$.ofInetSocketAddress()));
    }
  }

  public static final class bootstrapServers {
    public static Flag<?> getGlobalFlag() {
      return bootstrapServers$.MODULE$.getGlobalFlag();
    }
  }

  static String topic() {
    return topic$.MODULE$.apply();
  }

  public static final class topic$ extends GlobalFlag<String> {
    public static final topic$ MODULE$ = new topic$();

    @Override public String name() {
      return "zipkin.kafka.topic";
    }

    private topic$() {
      super("zipkin", "Kafka topic zipkin traces will be sent to", Flaggable$.MODULE$.ofString());
    }
  }

  public static final class topic {
    public static Flag<?> getGlobalFlag() {
      return topic$.MODULE$.getGlobalFlag();
    }
  }
}

