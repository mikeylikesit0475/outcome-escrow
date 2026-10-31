package io.outcomeescrow.domain.ledger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Ledger {
  private final Map<String, Posting> postingsByKey = new LinkedHashMap<>();

  public boolean post(Posting posting) {
    if (posting == null) {
      throw new IllegalArgumentException("posting is required");
    }
    if (Objects.equals(posting.fromAccount(), posting.toAccount())) {
      throw new IllegalArgumentException("accounts must differ");
    }
    if (posting.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (postingsByKey.containsKey(posting.idempotencyKey())) {
      return false;
    }
    postingsByKey.put(posting.idempotencyKey(), posting);
    return true;
  }

  public int size() {
    return postingsByKey.size();
  }

  public List<Posting> postings() {
    return List.copyOf(postingsByKey.values());
  }

  public record Account(String id, String currency) {
    public Account {
      if (id == null || id.isBlank()) {
        throw new IllegalArgumentException("id is required");
      }
    }
  }

  public record Posting(
      String idempotencyKey,
      Account fromAccount,
      Account toAccount,
      long amountMinor,
      String currency,
      Instant postedAt) {
    public Posting {
      if (amountMinor < 0) {
        throw new IllegalArgumentException("amount cannot be negative");
      }
    }
  }
}
