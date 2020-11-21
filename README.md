# zipkin-finagle

[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://github.com/openzipkin/zipkin-finagle/workflows/test/badge.svg)](https://github.com/openzipkin/zipkin-finagle/actions?query=workflow%3Atest)
[![Maven Central](https://img.shields.io/maven-central/v/io.zipkin.finagle2/zipkin-finagle_2.12.svg)](https://search.maven.org/search?q=g:io.zipkin.finagle2%20AND%20a:zipkin-finagle_2.12)

Integration between Finagle tracing to Zipkin transports including http, kafka and scribe.

## Quick start
Finagle will use a tracer that it detects in the classpath. For example, depending on `io.zipkin.finagle2:zipkin-finagle-http_2.12` will send to Zipkin over Http.

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

### Note on service names

Sometimes labels used in Finagle do not match the intent of the Zipkin service name. If your traces
or dependency graph looks incorrect, set the flag `zipkin.localServiceName`. This will ignore any of
the labels set by servers. In clients, the `ServiceName` recorded by Finagle will be moved to
`span.remoteEndpoint.serviceName` in Zipkin under the assumption that it was intended to name the
remote host.

## Configuration

### Global Flags
zipkin-finagle configuration is via [global flags](https://github.com/twitter/util/blob/master/util-app/src/main/scala/com/twitter/app/Flag.scala).

Global flags can either be set by system property, or commandline argument (ex if using TwitterServer).

Ex the following are equivalent ways to trace every request:
```bash
$ java -Dzipkin.localServiceName=favstar  -Dzipkin.initialSampleRate=1.0 ...
$ java -cp my-twitter-server.jar -zipkin.localServiceName=favstar -zipkin.initialSampleRate=1.0
```

Here are the flags that apply to all transports:

Flag | Default | Description
--- | --- | ---
zipkin.localServiceName | unknown | Overrides any ServiceName annotation set by Finagle. Controls Span.localEndpoint.serviceName in Zipkin.
zipkin.initialSampleRate | 0.001 (0.1%) | Percentage of traces to sample (report to zipkin) in the range [0.0 - 1.0]

### Http Configuration
Adding `io.zipkin.finagle2:zipkin-finagle-http_2.12` to your classpath will configure Finagle
to report trace data to a Zipkin server via HTTP.

Here are the flags that apply to Http:

Flag | Default | Description
--- | --- | ---
zipkin.http.host | localhost:9411 | The network location of the Zipkin http service. See http://twitter.github.io/finagle/guide/Names.html
zipkin.http.hostHeader | zipkin | The Host header used when sending spans to Zipkin
zipkin.http.path | /api/v2/spans | The path to the spans endpoint
zipkin.http.compressionEnabled | true | True implies that spans will be gzipped before transport
zipkin.http.tlsEnabled | false | Whether or not the Zipkin host uses TLS
zipkin.http.tlsValidationEnabled | true | Whether or not to enable TLS validation for the Zipkin host when TLS is enabled

Ex. Here's how to configure the Zipkin server with a system property:
```bash
$ java -Dzipkin.http.host=192.168.99.100:9411 ...
```

### Kafka Configuration
Adding `io.zipkin.finagle2:zipkin-finagle-kafka_2.12` to your classpath will configure Finagle
to report trace data to a Kafka topic. The minimum Kafka server version is 0.10

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
Adding `io.zipkin.finagle2:zipkin-finagle-scribe_2.12` to your classpath will configure Finagle
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

### Custom

You may need to use alternative reporters like [zipkin-aws](https://github.com/openzipkin/zipkin-aws/),
control configuration not available in flags, or change metrics reporting configuration. To do that,
use `ZipkinReporter.newBuilder()` and supply the arguments you need.

```java
// Setup a sender without using Finagle flags like so:
Properties overrides = new Properties();
overrides.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
sender = KafkaSender.newBuilder()
  .bootstrapServers("host1:9092,host2:9092")
  .overrides(overrides)
  .encoding(Encoding.PROTO3)
  .build();

zipkinStats = stats.scope("zipkin");
spanReporter = AsyncReporter.builder(sender)
  .metrics(new ReporterMetricsAdapter(zipkinStats)) // routes reporter metrics to finagle stats
  .build()

// Now, use it here, but don't forget to close the sender!
tracer = ZipkinTracer.newBuilder(spanReporter).stats(zipkinStats).build();
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

## Artifacts
All artifacts publish to the group ID "io.zipkin.finagle2". We use a common release version for all
components.

### Library Releases
Releases are at [Sonatype](https://oss.sonatype.org/content/repositories/releases) and [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22io.zipkin.finagle2%22)

### Library Snapshots
Snapshots are uploaded to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots) after
commits to master.
