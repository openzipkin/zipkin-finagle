/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.TraceId;
import java.util.Calendar;
import java.util.TimeZone;
import scala.Option;
import scala.collection.immutable.Seq;
import scala.collection.immutable.Seq$;
import scala.collection.mutable.Builder;

import static scala.Option.empty;

public final class FinagleTestObjects {
  public static final long TODAY = midnightUTC(System.currentTimeMillis());
  public static final SpanId traceId = SpanId.fromString("d2f9288a2904503d").get();
  public static final TraceId root = new TraceId(Option.apply(traceId), empty(), traceId, empty(),
      Flags$.MODULE$.apply());
  public static final TraceId child = new TraceId(Option.apply(traceId), Option.apply(traceId),
      SpanId.fromString("0f28590523a46541").get(), empty(), Flags$.MODULE$.apply());

  public static Seq<String> seq(String... entries) {
    // Raw Seq param to avoid generics conflict between scala 2.12 and 2.13
    Builder builder = Seq$.MODULE$.newBuilder();
    for (String entry: entries) builder.$plus$eq(entry);
    return (Seq<String>) builder.result();
  }

  static long midnightUTC(long epochMillis) {
    Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    day.setTimeInMillis(epochMillis);
    day.set(14, 0);
    day.set(13, 0);
    day.set(12, 0);
    day.set(11, 0);
    return day.getTimeInMillis();
  }
}
