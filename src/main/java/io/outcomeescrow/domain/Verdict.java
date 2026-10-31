package io.outcomeescrow.domain;

import java.time.Instant;

public record Verdict(
    String outcomeId,
    int score,
    Status status,
    TierTable.Tier tier,
    Instant evaluatedAt,
    int evidenceCount,
    String ruleVersion,
    String evidenceHeadHash,
    String explanation) {
  public Verdict {
    if (outcomeId == null || outcomeId.isBlank()) {
      throw new IllegalArgumentException("outcomeId is required");
    }
    if (ruleVersion == null || ruleVersion.isBlank()) {
      throw new IllegalArgumentException("ruleVersion is required");
    }
    if (tier == null) {
      throw new IllegalArgumentException("tier is required");
    }
    if (evaluatedAt == null) {
      throw new IllegalArgumentException("evaluatedAt is required");
    }
    if (evidenceCount < 0) {
      throw new IllegalArgumentException("evidenceCount is invalid");
    }
  }

  public enum Status {
    MET,
    PARTIAL,
    MISSED,
    INSUFFICIENT_EVIDENCE
  }
}
