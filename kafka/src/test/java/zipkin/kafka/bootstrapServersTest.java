/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin.kafka;

import com.twitter.app.Flag;
import com.twitter.app.GlobalFlag$;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Test;
import scala.Function0;
import scala.Option;
import scala.runtime.AbstractFunction0;
import scala.runtime.BoxedUnit;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static scala.collection.JavaConverters.asJavaCollection;

public class bootstrapServersTest {

  @After public void resetGlobalFlags() {
    for (Flag<?> globalFlag: globalFlags()) globalFlag.reset();
  }

  @Test public void defaultValue() {
    Option<Flag<?>> flagOption = GlobalFlag$.MODULE$.get("zipkin.kafka.bootstrapServers");
    assertThat(flagOption.get().apply())
        .isEqualTo(singletonList(new InetSocketAddress("localhost", 9092)));
  }

  @Test public void letOverridesDefault() {
    final List<InetSocketAddress> override = singletonList(new InetSocketAddress("zipkin", 9092));

    final AtomicBoolean ran = new AtomicBoolean();
    Function0<BoxedUnit> fn0 = new AbstractFunction0<BoxedUnit>() {
      @Override public BoxedUnit apply() {
        ran.set(true); // used to verify this block is executed.
        assertThat(bootstrapServers$.Flag.isDefined()).isTrue();
        assertThat(bootstrapServers$.Flag.apply()).isEqualTo(override);
        return BoxedUnit.UNIT;
      }
    };
    bootstrapServers$.Flag.let(override, fn0);

    assertThat(ran.get()).isTrue();
  }

  @Test
  public void registersGlobal() {
    assertThat(globalFlags())
        .extracting(f -> f.name())
        .containsOnlyOnce("zipkin.kafka.bootstrapServers");
  }

  Collection<Flag<?>> globalFlags() {
    return asJavaCollection(GlobalFlag$.MODULE$.getAll(getClass().getClassLoader()));
  }
}
