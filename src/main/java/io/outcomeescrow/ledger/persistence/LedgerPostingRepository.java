package io.outcomeescrow.ledger.persistence;

public interface LedgerPostingRepository {
  boolean append(LedgerPostingRecord record);

  void ensureAccount(String accountId, String currency);
}
