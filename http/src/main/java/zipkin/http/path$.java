/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class path$ extends JavaGlobalFlag<String> {
  public static String DEFAULT_PATH = "/api/v2/spans";

  private path$() {
    super(DEFAULT_PATH,
        "The path to the spans endpoint",
        Flaggable.ofString());
  }

  public static final Flag<String> Flag = new path$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}

