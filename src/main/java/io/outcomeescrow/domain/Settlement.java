package io.outcomeescrow.domain;

import java.time.Instant;

public record Settlement(
    String outcomeId,
    String verdictId,
    String evidenceHeadHash,
    String ruleVersion,
    Kind kind,
    Money amount,
    String currency,
    Instant settledAt) {

  public Settlement {
    if (outcomeId == null || outcomeId.isBlank()) {
      throw new IllegalArgumentException("outcomeId is required");
    }
    if (verdictId == null || verdictId.isBlank()) {
      throw new IllegalArgumentException("verdictId is required");
    }
    if (kind == null) {
      throw new IllegalArgumentException("kind is required");
    }
    if (amount == null) {
      throw new IllegalArgumentException("amount is required");
    }
    if (settledAt == null) {
      throw new IllegalArgumentException("settledAt is required");
    }
  }

  public enum Kind {
    PAYOUT,
    CLAWBACK,
    NONE
  }

  public static Settlement from(
      Outcome outcome, Verdict verdict, TierTable tierTable, Instant settledAt) {
    if (outcome == null || verdict == null || tierTable == null || settledAt == null) {
      throw new IllegalArgumentException("settlement inputs are required");
    }
    long payoutMinor =
        tierTable.payout(
            Money.of(outcome.milestoneValueMinor(), outcome.currency()), verdict.score());
    Kind kind;
    long amountMinor;
    if (payoutMinor > 0) {
      kind = Kind.PAYOUT;
      amountMinor = payoutMinor;
    } else if (payoutMinor < 0) {
      kind = Kind.CLAWBACK;
      amountMinor = -payoutMinor;
    } else {
      kind = Kind.NONE;
      amountMinor = 0;
    }
    return new Settlement(
        outcome.id(),
        verdict.outcomeId(),
        verdict.evidenceHeadHash(),
        verdict.ruleVersion(),
        kind,
        Money.of(amountMinor, outcome.currency()),
        outcome.currency(),
        settledAt);
  }
}
