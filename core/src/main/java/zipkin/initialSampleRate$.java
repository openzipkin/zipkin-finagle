/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class initialSampleRate$ extends JavaGlobalFlag<Float> {
  private initialSampleRate$() {
    super(0.001f, "Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0]",
        Flaggable.ofJavaFloat());
  }

  public static final Flag<Float> Flag = new initialSampleRate$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
