/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import com.twitter.util.Duration;
import com.twitter.util.Time;
import com.twitter.util.Time$;
import com.twitter.util.TimeControl;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import scala.runtime.AbstractFunction1;

public final class WithTimeAt implements TestRule, TimeControl {
  private final static ThreadLocal<TimeControl> holder = new ThreadLocal<>();
  private final long epochMillis;

  public WithTimeAt(long epochMillis) {
    this.epochMillis = epochMillis;
  }

  @Override public void set(Time time) {
    holder.get().set(time);
  }

  @Override public void advance(Duration delta) {
    holder.get().advance(delta);
  }

  @Override
  public Statement apply(Statement base, Description description) {
    return new Statement() {
      AtomicReference<Throwable> throwable = new AtomicReference<>();

      @Override public void evaluate() throws Throwable {
        Time$.MODULE$.withTimeAt(Time.fromMilliseconds(epochMillis),
            new AbstractFunction1<TimeControl, Object>() {
              @Override public Object apply(TimeControl tc) {
                holder.set(tc);
                try {
                  base.evaluate();
                } catch (Throwable t) {
                  throwable.set(t);
                }
                return null;
              }
            });
        Throwable t = throwable.get();
        if (t != null) throw t;
      }
    };
  }
}
