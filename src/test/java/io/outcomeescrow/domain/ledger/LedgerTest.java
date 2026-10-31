package io.outcomeescrow.domain.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.outcomeescrow.domain.ledger.Ledger.Account;
import io.outcomeescrow.domain.ledger.Ledger.Posting;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LedgerTest {

  @Test
  void duplicateIdempotencyKeyIsNoop() {
    var ledger = new Ledger();
    var accountA = new Account("escrow", "USD");
    var accountB = new Account("vendor", "USD");

    var posting =
        new Posting(
            "key-1", accountA, accountB, 1000L, "USD", Instant.parse("2026-01-01T00:00:00Z"));

    assertTrue(ledger.post(posting));
    assertFalse(ledger.post(posting));
    assertEquals(1, ledger.size());
  }
}
