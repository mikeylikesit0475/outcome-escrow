package io.outcomeescrow.domain;

public record Verdict(
    String outcomeId, int score, Status status, String ruleVersion, String evidenceHeadHash) {
  public Verdict {
    if (outcomeId == null || outcomeId.isBlank()) {
      throw new IllegalArgumentException("outcomeId is required");
    }
    if (ruleVersion == null || ruleVersion.isBlank()) {
      throw new IllegalArgumentException("ruleVersion is required");
    }
  }

  public enum Status {
    MET,
    PARTIAL,
    MISSED,
    INSUFFICIENT_EVIDENCE
  }
}
