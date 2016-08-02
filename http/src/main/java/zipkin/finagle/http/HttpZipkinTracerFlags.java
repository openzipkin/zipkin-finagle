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
package zipkin.finagle.http;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable$;
import com.twitter.app.GlobalFlag;

/**
 * This class contains a port of all the global flags
 */
public final class HttpZipkinTracerFlags {

  static String host() {
    return host$.MODULE$.apply();
  }

  public static final class host$ extends GlobalFlag<String> {
    public static final host$ MODULE$ = new host$();

    @Override public String name() {
      return "zipkin.http.host";
    }

    private host$() {
      super("localhost:9411", "Zipkin server listening on http; also used as the Host header",
          Flaggable$.MODULE$.ofString());
    }
  }

  public static final class host {
    public static Flag<?> getGlobalFlag() {
      return host$.MODULE$.getGlobalFlag();
    }
  }
}

