[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin) [![Build Status](https://travis-ci.org/openzipkin/zipkin-finagle.svg?branch=master)](https://travis-ci.org/openzipkin/zipkin-finagle) [![Download](https://api.bintray.com/packages/openzipkin/maven/zipkin-finagle/images/download.svg) ](https://bintray.com/openzipkin/maven/zipkin-finagle/_latestVersion)

# zipkin-finagle
Integration between Finagle tracing to Zipkin transports including http, kafka and scribe.

## Quick start
Finagle will use a tracer that it detects in the classpath. For example, depending on `io.zipkin.finagle:zipkin-finagle-http_2.11` will send to Zipkin over Http.

You can look at the [example](https://github.com/openzipkin/zipkin-finagle-example) for this in use.

## Choosing a tracer explicitly
You can also explicitly choose a tracer, which is more reliable, at the tradeoff of more configuration.

Here an example initializing an http client in scala and java
```scala
client = Http.client
             .withTracer(new HttpZipkinTracer())
             .withLabel("frontend") // becomes the zipkin service name
             .newService("remotehost:8080");
```

```java
client = Http$.MODULE$.client()
              .withTracer(new HttpZipkinTracer())
              .withLabel("frontend") // becomes the zipkin service name
              .newService("remotehost:8080");
```

## Configuration

### Global Flags
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
zipkin.http.host | localhost:9411 | The network location of the Zipkin http service. See http://twitter.github.io/finagle/guide/Names.html
zipkin.http.hostHeader | zipkin | The Host header used when sending spans to Zipkin
zipkin.http.compressionEnabled | true | True implies that spans will be gzipped before transport

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

### Scribe Configuration
Adding `io.zipkin.finagle:zipkin-finagle-scribe_2.11` to your classpath will configure Finagle
to report trace data to the zipkin category of Scribe.

Here are the flags that apply to Scribe:

Flag | Default | Description
--- | --- | ---
zipkin.scribe.host | localhost:1463 | The network location of the Scribe service. See http://twitter.github.io/finagle/guide/Names.html

Ex. Here's how to configure the Scribe host with a system property. In this case pointing directly to a zipkin server:
```bash
$ java -Dzipkin.scribe.host=zipkinhost:9410 ...
```

### Programmatic

You can also configure zipkin tracers programmatically. Here's an example:

```java
HttpZipkinTracer.Config config = HttpZipkinTracer.Config.builder()
    // The frontend makes a sampling decision (via Trace.letTracerAndId) and propagates it downstream.
    // This property says sample 100% of traces.
    .initialSampleRate(1.0f)
    // All servers need to point to the same zipkin transport
    .host("127.0.0.1:9411").build();

Tracer tracer = HttpZipkinTracer.create(config,
    // print stats about zipkin to the console
    new JavaLoggerStatsReceiver(Logger.getAnonymousLogger()));
```

## Metrics

The following metrics are reported under a transport-specific category
to the configured `StatsReceiver`. For example, if using kafka, they are
reported relative to "zipkin.kafka".

Metric | Description
--- | ---
spans | the count of spans recorded by the tracer
span_bytes | the count of encoded span bytes recorded by the tracer
spans_dropped | the count of spans dropped for any reason. For example, failure queueing or sending.
messages | the count of messages sent to zipkin. Ex POST requests or Kafka messages.
message_bytes | the count of encoded message bytes sent. This includes encoding overhead and excludes compression.
messages_dropped/exception_class_name+ | count of messages dropped broken down by cause.
span_queue_size | last count of spans in the pending queue
span_queue_bytes | last count of encoded span bytes in the pending queue
