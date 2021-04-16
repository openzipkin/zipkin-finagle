/*
 * Copyright 2016-2021 The OpenZipkin Authors
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
package zipkin;

import com.twitter.app.Flag;
import com.twitter.app.Flaggable;
import com.twitter.app.JavaGlobalFlag;

public final class localServiceName$ extends JavaGlobalFlag<String> {
  private localServiceName$() {
    super("unknown", "The localEndpoint.serviceName to use for all spans sent to Zipkin",
        Flaggable.ofString());
  }

  public static final com.twitter.app.Flag<String> Flag = new localServiceName$();

  public static Flag<?> globalFlagInstance() {
    return Flag;
  }
}
