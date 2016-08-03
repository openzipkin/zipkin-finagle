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

import com.twitter.app.GlobalFlag$;
import java.net.InetSocketAddress;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static scala.collection.JavaConversions.asJavaCollection;

public class KafkaZipkinTracerFlagsTest {

  @Test
  public void flagNamespace() {
    assertThat(KafkaZipkinTracerFlags.bootstrapServers.getGlobalFlag().name())
        .isEqualTo("zipkin.kafka.bootstrapServers");
    assertThat(KafkaZipkinTracerFlags.topic.getGlobalFlag().name())
        .isEqualTo("zipkin.kafka.topic");
  }

  @Test
  public void registersGlobalFlags() {
    assertThat(
        asJavaCollection(GlobalFlag$.MODULE$.getAll(KafkaZipkinTracerFlags.class.getClassLoader())))
        .containsOnlyOnce(
            KafkaZipkinTracerFlags.bootstrapServers.getGlobalFlag(),
            KafkaZipkinTracerFlags.topic.getGlobalFlag()
        );
  }

  @Test
  public void bootstrapServersParsesMultipleHosts() {
    KafkaZipkinTracerFlags.bootstrapServers.getGlobalFlag().parse("host1:9092,host2:9092");

    assertThat(asJavaCollection(KafkaZipkinTracerFlags.bootstrapServers$.MODULE$.apply()))
        .containsExactly(new InetSocketAddress("host1", 9092),
            new InetSocketAddress("host2", 9092));

    assertThat(KafkaZipkinTracerFlags.bootstrapServers())
        .isEqualTo("host1:9092,host2:9092");
  }
}
