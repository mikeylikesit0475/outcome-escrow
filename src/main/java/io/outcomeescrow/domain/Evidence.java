package io.outcomeescrow.domain;

import java.time.Instant;

public record Evidence(
    String id,
    String contractId,
    String outcomeId,
    String source,
    String sourceRef,
    Instant capturedAt,
    String payload,
    String hash,
    String prevHash,
    boolean synthetic) {
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

  public Evidence(
      String source,
      String sourceRef,
      Instant capturedAt,
      String payload,
      String hash,
      String prevHash) {
    this(null, null, null, source, sourceRef, capturedAt, payload, hash, prevHash, false);
  }
}
