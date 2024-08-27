/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package zipkin2.finagle;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import zipkin2.Span;
import zipkin2.codec.BytesDecoder;
import zipkin2.codec.BytesEncoder;
import zipkin2.codec.SpanBytesDecoder;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.BytesMessageEncoder;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.Encoding;

public final class FakeSender implements BytesMessageSender {
  static FakeSender create() {
    return new FakeSender(
        Encoding.THRIFT,
        Integer.MAX_VALUE,
        BytesMessageEncoder.forEncoding(Encoding.THRIFT),
        SpanBytesEncoder.THRIFT,
        SpanBytesDecoder.THRIFT,
        spans -> {
        }
    );
  }

  final Encoding encoding;
  final int messageMaxBytes;
  final BytesMessageEncoder messageEncoder;
  final BytesEncoder<Span> encoder;
  final BytesDecoder<Span> decoder;
  final Consumer<List<Span>> onSpans;

  FakeSender(
      Encoding encoding,
      int messageMaxBytes,
      BytesMessageEncoder messageEncoder,
      BytesEncoder<Span> encoder,
      BytesDecoder<Span> decoder,
      Consumer<List<Span>> onSpans
  ) {
    this.encoding = encoding;
    this.messageMaxBytes = messageMaxBytes;
    this.messageEncoder = messageEncoder;
    this.encoder = encoder;
    this.decoder = decoder;
    this.onSpans = onSpans;
  }

  FakeSender onSpans(Consumer<List<Span>> onSpans) {
    return new FakeSender(
        encoding,
        messageMaxBytes,
        messageEncoder,
        encoder,
        decoder,
        onSpans
    );
  }

  @Override public Encoding encoding() {
    return encoding;
  }

  @Override public int messageMaxBytes() {
    return messageMaxBytes;
  }

  @Override public int messageSizeInBytes(List<byte[]> encodedSpans) {
    return encoding.listSizeInBytes(encodedSpans);
  }

  @Override public int messageSizeInBytes(int encodedSizeInBytes) {
    return encoding.listSizeInBytes(encodedSizeInBytes);
  }

  /** close is typically called from a different thread */
  volatile boolean closeCalled;

  @Override public void send(List<byte[]> encodedSpans) {
    if (closeCalled) throw new IllegalStateException("closed");
    List<Span> decoded = encodedSpans.stream()
        .map(decoder::decodeOne)
        .collect(Collectors.toList());
    onSpans.accept(decoded);
  }

  @Override public void close() {
    closeCalled = true;
  }

  @Override public String toString() {
    return "FakeSender";
  }
}
