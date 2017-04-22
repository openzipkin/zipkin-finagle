/**
 * Copyright 2016-2017 The OpenZipkin Authors
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

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KafkaZipkinTracerTest {

  @Test
  public void bootstrapServersParsesMultipleHosts() {
    zipkin.kafka.bootstrapServers$.Flag.parse("host1:9092,host2:9092");

    assertThat(KafkaZipkinTracer.Config.builder().build().bootstrapServers())
        .isEqualTo("host1:9092,host2:9092");
  }
}
