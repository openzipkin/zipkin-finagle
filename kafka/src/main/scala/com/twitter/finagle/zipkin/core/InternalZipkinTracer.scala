package com.twitter.finagle.zipkin.core

import com.twitter.finagle.stats.StatsReceiver
import com.twitter.finagle.util.DefaultTimer
import com.twitter.util._

// TODO: RawZipkinTracer is package private in finagle!
// https://github.com/twitter/finagle/pull/528
abstract class InternalZipkinTracer(
  statsReceiver: StatsReceiver,
  timer: Timer = DefaultTimer.twitter
) extends com.twitter.finagle.zipkin.core.RawZipkinTracer(statsReceiver, timer) {
}
