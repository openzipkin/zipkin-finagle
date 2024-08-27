/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle.kafka;

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
