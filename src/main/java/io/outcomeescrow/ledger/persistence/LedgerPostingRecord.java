package io.outcomeescrow.ledger.persistence;

import java.time.Instant;

public record LedgerPostingRecord(
    String id,
    String idempotencyKey,
    String fromAccountId,
    String toAccountId,
    long amountMinor,
    String currency,
    Instant postedAt) {}
