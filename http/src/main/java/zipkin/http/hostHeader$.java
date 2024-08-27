/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class hostHeader$ extends JavaGlobalFlag<String> {
  private hostHeader$() {
    super("zipkin", "The Host header used when sending spans to Zipkin", Flaggable.ofString());
  }

  public static final Flag<String> Flag = new hostHeader$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
