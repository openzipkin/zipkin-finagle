/**
 * Copyright 2016 The OpenZipkin Authors
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
package zipkin.finagle;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable$;
import com.twitter.app.GlobalFlag;
import scala.runtime.BoxesRunTime;

/**
 * This class contains flags common to all zipkin tracers
 */
public final class ZipkinTracerFlags {

  public static float initialSampleRate() {
    return initialSampleRate$.MODULE$.apply();
  }

  public static final class initialSampleRate$ extends GlobalFlag<Float> {
    public static final initialSampleRate$ MODULE$ = new initialSampleRate$();

    // Default is 0.001 = 0.1% (let one in a 1000nd pass)
    private initialSampleRate$() {
      super(
          BoxesRunTime.boxToFloat(0.001f),
          "Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0]",
          Flaggable$.MODULE$.ofJavaFloat());
    }

    @Override public String name() {
      return "zipkin.initialSampleRate";
    }
  }

  public static final class initialSampleRate {
    public static Flag<?> getGlobalFlag() {
      return initialSampleRate$.MODULE$.getGlobalFlag();
    }
  }
}

