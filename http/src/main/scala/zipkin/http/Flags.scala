// intentionally in package zipkin.http, not zipkin.finagle.http as this
// translates directly to a property name which is only relevant in finagle.
// Ex. -zipkin.http.host=localhost:9411 vs -zipkin.finagle.http.host=localhost:9411
package zipkin.http

import com.twitter.app.GlobalFlag

object host extends GlobalFlag[String](
  "localhost:9411",
  "Zipkin server listening on http; also used as the Host header")
