/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class tlsEnabled$ extends JavaGlobalFlag<Boolean> {
  private tlsEnabled$() {
    super(false, "Whether or not the Zipkin host uses TLS",
        Flaggable.ofJavaBoolean());
  }

  public static final Flag<Boolean> Flag = new tlsEnabled$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
