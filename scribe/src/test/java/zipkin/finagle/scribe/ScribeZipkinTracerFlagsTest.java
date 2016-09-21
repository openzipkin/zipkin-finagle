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

import com.twitter.app.GlobalFlag$;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static scala.collection.JavaConversions.asJavaCollection;

public class ScribeZipkinTracerFlagsTest {

  @Test
  public void flagNamespace() {
    assertThat(ScribeZipkinTracerFlags.host.getGlobalFlag().name())
        .isEqualTo("zipkin.scribe.host");
  }

  @Test
  public void registersGlobalFlags() {
    assertThat(
        asJavaCollection(GlobalFlag$.MODULE$.getAll(ScribeZipkinTracerFlags.class.getClassLoader())))
        .containsOnlyOnce(
            ScribeZipkinTracerFlags.host.getGlobalFlag()
        );
  }
}
