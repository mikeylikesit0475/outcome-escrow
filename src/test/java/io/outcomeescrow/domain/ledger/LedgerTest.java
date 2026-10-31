package io.outcomeescrow.domain.ledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.outcomeescrow.domain.Settlement;
import io.outcomeescrow.domain.TierTable;
import io.outcomeescrow.domain.ledger.Ledger.Account;
import io.outcomeescrow.domain.ledger.Ledger.Posting;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerTest {

  @Test
  void duplicateIdempotencyKeyIsNoop() {
    var ledger = new Ledger();
    var posting =
        new Posting(
            "key-1",
            Account.ESCROW,
            Account.VENDOR_PAYABLE,
            1000L,
            "USD",
            "v1",
            "outcome-1",
            Instant.parse("2026-01-01T00:00:00Z"));

    assertTrue(ledger.post(posting));
    assertFalse(ledger.post(posting));
    assertEquals(1, ledger.size());
    assertEquals(-1000L, ledger.balance(Account.ESCROW));
    assertEquals(1000L, ledger.balance(Account.VENDOR_PAYABLE));
  }

  @Test
  void postsPayoutSettlementUsingNamedAccounts() {
    var ledger = new Ledger();
    TierTable tiers =
        new TierTable(0, 0, List.of(new TierTable.Tier(0, 0), new TierTable.Tier(10_000, 10_000)));
    var outcome =
        new io.outcomeescrow.domain.Outcome(
            "outcome-payout",
            "payout",
            new io.outcomeescrow.domain.RuleSpec.Deadline(
                "deploy", Instant.parse("2026-02-15T00:00:00Z")),
            1000L,
            "USD");
    var verdict =
        new io.outcomeescrow.domain.Verdict(
            "outcome-payout",
            10_000,
            io.outcomeescrow.domain.Verdict.Status.MET,
            tiers.tierForScore(10_000),
            Instant.parse("2026-02-15T00:00:00Z"),
            1,
            "version",
            "hash",
            "payout");
    var settlement =
        Settlement.from(outcome, verdict, tiers, Instant.parse("2026-02-15T00:00:00Z"));
    assertTrue(ledger.post(settlement));
    assertEquals(Settlement.Kind.PAYOUT, settlement.kind());
    assertEquals(1000L, settlement.amount().amountMinor());
    assertEquals(-1000L, ledger.balance(Account.ESCROW));
    assertEquals(1000L, ledger.balance(Account.VENDOR_PAYABLE));
  }
}
