/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.kafka;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class topic$ extends JavaGlobalFlag<String> {
  private topic$() {
    super("zipkin", "Kafka topic zipkin traces will be sent to", Flaggable.ofString());
  }

  public static final Flag<String> Flag = new topic$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
