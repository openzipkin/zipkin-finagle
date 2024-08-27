/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class localServiceName$ extends JavaGlobalFlag<String> {
  private localServiceName$() {
    super("unknown", "The localEndpoint.serviceName to use for all spans sent to Zipkin",
        Flaggable.ofString());
  }

  public static final com.twitter.app.Flag<String> Flag = new localServiceName$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
