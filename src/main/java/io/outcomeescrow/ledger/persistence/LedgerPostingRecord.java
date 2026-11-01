package io.outcomeescrow.ledger.persistence;

import java.time.Instant;

public record LedgerPostingRecord(
    String id,
    String idempotencyKey,
    String fromAccountId,
    String toAccountId,
    long amountMinor,
    String currency,
    Instant postedAt,
    String verdictId,
    String outcomeId) {

  public LedgerPostingRecord(
      String id,
      String idempotencyKey,
      String fromAccountId,
      String toAccountId,
      long amountMinor,
      String currency,
      Instant postedAt) {
    this(
        id,
        idempotencyKey,
        fromAccountId,
        toAccountId,
        amountMinor,
        currency,
        postedAt,
        null,
        null);
  }
}
