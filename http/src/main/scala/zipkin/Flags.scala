// intentionally in package zipkin, not zipkin.finagle as this
// translates directly to a property name which is only relevant in finagle.
// Ex. -zipkin.initialSampleRate=1.0 vs -zipkin.finagle.initialSampleRate=1.0
package zipkin

import com.twitter.app.GlobalFlag
import com.twitter.finagle.zipkin.core.Sampler

object initialSampleRate extends GlobalFlag[Float](
  Sampler.DefaultSampleRate,
  "Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0]")
