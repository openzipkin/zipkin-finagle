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

import com.twitter.finagle.tracing.Flags$;
import com.twitter.finagle.tracing.SpanId;
import com.twitter.finagle.tracing.TraceId;
import scala.Option;
import scala.collection.JavaConversions;
import scala.collection.mutable.Seq;

import static java.util.Arrays.asList;
import static scala.Option.empty;
import static zipkin.internal.Util.midnightUTC;

public final class FinagleTestObjects {
  public static final long TODAY = midnightUTC(System.currentTimeMillis());
  public static final SpanId traceId = SpanId.fromString("d2f9288a2904503d").get();
  public static final TraceId root = new TraceId(Option.apply(traceId), empty(), traceId, empty(),
      Flags$.MODULE$.apply());
  public static final TraceId child = new TraceId(Option.apply(traceId), Option.apply(traceId),
      SpanId.fromString("0f28590523a46541").get(), empty(), Flags$.MODULE$.apply());


  public static Seq<String> seq(String... entries) {
    return JavaConversions.asScalaBuffer(asList(entries));
  }
}
