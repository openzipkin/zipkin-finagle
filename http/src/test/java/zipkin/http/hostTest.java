/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.http;

import com.twitter.app.Flag;
import com.twitter.app.GlobalFlag$;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Test;
import scala.Function0;
import scala.Option;
import scala.runtime.AbstractFunction0;
import scala.runtime.BoxedUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static scala.collection.JavaConverters.asJavaCollection;

public class hostTest {

  @After public void resetGlobalFlags() {
    for (Flag<?> globalFlag: globalFlags()) globalFlag.reset();
  }

  @Test public void defaultValue() {
    Option<Flag<?>> flagOption = GlobalFlag$.MODULE$.get("zipkin.http.host");
    assertThat(flagOption.get().apply()).isEqualTo("localhost:9411");
  }

  @Test public void letOverridesDefault() {
    final String override = "foo:9411";

    final AtomicBoolean ran = new AtomicBoolean();
    Function0<BoxedUnit> fn0 = new AbstractFunction0<BoxedUnit>() {
      @Override public BoxedUnit apply() {
        ran.set(true); // used to verify this block is executed.
        assertThat(host$.Flag.isDefined()).isTrue();
        assertThat(host$.Flag.apply()).isEqualTo(override);
        return BoxedUnit.UNIT;
      }
    };
    host$.Flag.let(override, fn0);

    assertThat(ran.get()).isTrue();
  }

  @Test
  public void registersGlobal() {
    assertThat(globalFlags())
        .extracting(f -> f.name())
        .containsOnlyOnce("zipkin.http.host");
  }

  Collection<Flag<?>> globalFlags() {
    return asJavaCollection(GlobalFlag$.MODULE$.getAll(getClass().getClassLoader()));
  }
}
