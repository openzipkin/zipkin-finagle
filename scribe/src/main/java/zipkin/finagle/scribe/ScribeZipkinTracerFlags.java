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
package zipkin.finagle.scribe;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable$;
import com.twitter.app.GlobalFlag;

public final class ScribeZipkinTracerFlags {

  static String host() {
    return host$.MODULE$.apply();
  }

  public static final class host$ extends GlobalFlag<String> {
    public static final host$ MODULE$ = new host$();

    private host$() {
      super("localhost:1463",
          "The network location of the Scribe service. See http://twitter.github.io/finagle/guide/Names.html",
          Flaggable$.MODULE$.ofString());
    }

    @Override public String name() {
      return "zipkin.scribe.host";
    }
  }

  public static final class host {
    public static Flag<?> getGlobalFlag() {
      return host$.MODULE$.getGlobalFlag();
    }
  }
}
