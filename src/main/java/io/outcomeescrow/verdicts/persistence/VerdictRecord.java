package io.outcomeescrow.verdicts.persistence;

import java.time.Instant;

public record VerdictRecord(
    String id,
    String outcomeId,
    int score,
    String status,
    String ruleVersion,
    String evidenceHeadHash,
    Instant evaluatedAt,
    int evidenceCount,
    int tierMinimumScore,
    int tierPayoutBasisPoints,
    String explanation) {

  public static VerdictRecord from(
      String id, io.outcomeescrow.domain.Verdict verdict, int evidenceCount) {
    return new VerdictRecord(
        id,
        verdict.outcomeId(),
        verdict.score(),
        verdict.status().name(),
        verdict.ruleVersion(),
        verdict.evidenceHeadHash(),
        verdict.evaluatedAt(),
        evidenceCount,
        verdict.tier() == null ? 0 : verdict.tier().minimumScore(),
        verdict.tier() == null ? 0 : verdict.tier().payoutBasisPoints(),
        verdict.explanation());
  }
}
