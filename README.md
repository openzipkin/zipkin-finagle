[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin-dependencies.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin-dependencies) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin-dependencies/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin-dependencies/_latestVersion)

# zipkin-finagle
Integration between Finagle tracing to Zipkin transports including http and kafka.

## Quick start
Finagle will use a tracer that it detects in the classpath. For example, depending on `io.zipkin.finagle:zipkin-finagle-http_2.11` will send to Zipkin over Http.

You can look at the [example](https://github.com/openzipkin/zipkin-finagle-example) for this in use.

## Choosing a tracer explicitly
You can also explicitly choose a tracer, which is more reliable, at the tradeoff of more configuration.

Here an example initializing an http client in scala and java
```scala
client = ClientBuilder()
  .codec(Http().enableTracing(true))
  .tracer(new HttpZipkinTracer())
  .name("frontend") // becomes the zipkin service name
  .hosts("remotehost:8080").build()
```

```java
client = ClientBuilder.safeBuild(ClientBuilder.get()
  .codec(Http.get().enableTracing(true))
  .tracer(new HttpZipkinTracer())
  .name("frontend") // becomes the zipkin service name
  .hosts("remotehost:8080"));
```

## Configuration
zipkin-finagle configuration is via [global flags](https://github.com/twitter/util/blob/master/util-app/src/main/scala/com/twitter/app/Flag.scala).

Global flags can either be set by system property, or commandline argument (ex if using TwitterServer).

Ex the following are equivalent ways to trace every request:
```bash
$ java -Dzipkin.initialSampleRate=1.0 ...
$ java -cp my-twitter-server.jar -zipkin.initialSampleRate=1.0
```

Here are the flags that apply to all transports:

Flag | Default | Description
--- | --- | ---
zipkin.initialSampleRate | 0.001 (0.1%) | Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0]

### Http Configuration
Adding `io.zipkin.finagle:zipkin-finagle-http_2.11` to your classpath will configure Finagle
to report trace data to a Zipkin server via HTTP.

Here are the flags that apply to Http:

Flag | Default | Description
--- | --- | ---
zipkin.http.host | localhost:9411 | Zipkin server listening on http; also used as the Host header

Ex. Here's how to configure the Zipkin server with a system property:
```bash
$ java -Dzipkin.http.host=192.168.99.100:9411 ...
```

### Kafka Configuration
Adding `io.zipkin.finagle:zipkin-finagle-kafka_2.11` to your classpath will configure Finagle
to report trace data to a Kafka topic. The minimum Kafka server version is 0.8.2.2

Here are the flags that apply to Kafka:

Flag | Default | Description
--- | --- | ---
zipkin.kafka.bootstrapServers | localhost:9092 | Initial set of kafka servers to connect to, rest of cluster will be discovered (comma separated)
zipkin.kafka.topic | zipkin | Kafka topic zipkin traces will be sent to

Ex. Here's how to configure the Kafka server with a system property:
```bash
$ java -Dzipkin.kafka.bootstrapServers=192.168.99.100 ...
```
