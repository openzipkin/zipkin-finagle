/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class tlsValidationEnabled$ extends JavaGlobalFlag<Boolean> {
  private tlsValidationEnabled$() {
    super(true, "Whether or not to enable TLS validation for the Zipkin host when TLS is enabled",
        Flaggable.ofJavaBoolean());
  }

  public static final Flag<Boolean> Flag = new tlsValidationEnabled$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
