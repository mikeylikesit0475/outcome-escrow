package io.outcomeescrow.domain;

import java.time.Instant;

public record Evidence(
    String source,
    String sourceRef,
    Instant capturedAt,
    String payload,
    String hash,
    String prevHash) {
  public Evidence {
    if (source == null || source.isBlank()) {
      throw new IllegalArgumentException("source is required");
    }
    if (hash == null || hash.isBlank()) {
      throw new IllegalArgumentException("hash is required");
    }
    if (capturedAt == null) {
      capturedAt = Instant.EPOCH;
    }
  }
}
