/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class host$ extends JavaGlobalFlag<String> {
  private host$() {
    super("localhost:9411",
        "The network location of the Zipkin http service. See http://twitter.github.io/finagle/guide/Names.html",
        Flaggable.ofString());
  }

  public static final Flag<String> Flag = new host$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
