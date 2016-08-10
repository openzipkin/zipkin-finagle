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

    private host$() {
      super("localhost:9411",
          "The network location of the Zipkin http service. See http://twitter.github.io/finagle/guide/Names.html",
          Flaggable$.MODULE$.ofString());
    }

    @Override public String name() {
      return "zipkin.http.host";
    }
  }

  public static final class host {
    public static Flag<?> getGlobalFlag() {
      return host$.MODULE$.getGlobalFlag();
    }
  }

  static String hostHeader() {
    return hostHeader$.MODULE$.apply();
  }

  public static final class hostHeader$ extends GlobalFlag<String> {
    public static final hostHeader$ MODULE$ = new hostHeader$();

    private hostHeader$() {
      super("zipkin", "The Host header used when sending spans to Zipkin",
          Flaggable$.MODULE$.ofString());
    }

    @Override public String name() {
      return "zipkin.http.hostHeader";
    }
  }

  public static final class hostHeader {
    public static Flag<?> getGlobalFlag() {
      return hostHeader$.MODULE$.getGlobalFlag();
    }
  }

  static boolean compressionEnabled() {
    return compressionEnabled$.MODULE$.apply();
  }

  public static final class compressionEnabled$ extends GlobalFlag<Boolean> {
    public static final compressionEnabled$ MODULE$ = new compressionEnabled$();

    private compressionEnabled$() {
      super(true, "True implies that spans will be gzipped before transport",
          Flaggable$.MODULE$.ofJavaBoolean());
    }

    @Override public String name() {
      return "zipkin.http.compressionEnabled";
    }
  }

  public static final class compressionEnabled {
    public static Flag<?> getGlobalFlag() {
      return compressionEnabled$.MODULE$.getGlobalFlag();
    }
  }
}

