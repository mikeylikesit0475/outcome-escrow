package io.outcomeescrow.domain.ledger;

import io.outcomeescrow.domain.Settlement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Ledger {
  private final Map<String, Posting> postingsByKey = new LinkedHashMap<>();
  private final Map<Account, Long> balances = new EnumMap<>(Account.class);

  public Ledger() {
    for (Account account : Account.values()) {
      balances.put(account, 0L);
    }
  }

  public boolean post(Posting posting) {
    if (posting == null) {
      throw new IllegalArgumentException("posting is required");
    }
    if (posting.amountMinor() < 0) {
      throw new IllegalArgumentException("amount cannot be negative");
    }
    if (posting.idempotencyKey() == null || posting.idempotencyKey().isBlank()) {
      throw new IllegalArgumentException("idempotencyKey is required");
    }
    if (posting.debit() == null || posting.credit() == null) {
      throw new IllegalArgumentException("accounts are required");
    }
    if (posting.debit() == posting.credit()) {
      throw new IllegalArgumentException("accounts must differ");
    }
    if (postingsByKey.containsKey(posting.idempotencyKey())) {
      return false;
    }
    postingsByKey.put(posting.idempotencyKey(), posting);
    balances.put(posting.debit(), balances.get(posting.debit()) - posting.amountMinor());
    balances.put(posting.credit(), balances.get(posting.credit()) + posting.amountMinor());
    return true;
  }

  public boolean post(Settlement settlement) {
    return post(settlement, settlement.settledAt());
  }

  public boolean post(Settlement settlement, Instant postedAt) {
    if (settlement == null) {
      throw new IllegalArgumentException("settlement is required");
    }
    if (postedAt == null) {
      throw new IllegalArgumentException("postedAt is required");
    }
    if (settlement.kind() == Settlement.Kind.NONE) {
      return false;
    }
    Account debit = postingDirection(settlement.kind()).debit();
    Account credit = postingDirection(settlement.kind()).credit();
    Posting posting =
        new Posting(
            idempotencyKey(
                settlement.outcomeId(), settlement.evidenceHeadHash(), settlement.ruleVersion()),
            debit,
            credit,
            settlement.amount().amountMinor(),
            settlement.currency(),
            settlement.verdictId(),
            settlement.outcomeId(),
            postedAt);
    return post(posting);
  }

  public long balance(Account account) {
    if (account == null) {
      throw new IllegalArgumentException("account is required");
    }
    return balances.getOrDefault(account, 0L);
  }

  public List<Posting> postings() {
    return List.copyOf(postingsByKey.values());
  }

  public List<Posting> postingsByVerdict(String verdictId) {
    return postingsByKey.values().stream()
        .filter(posting -> Objects.equals(posting.verdictId(), verdictId))
        .toList();
  }

  public int size() {
    return postingsByKey.size();
  }

  // Named accounts for every contract.
  public enum Account {
    ESCROW,
    VENDOR_PAYABLE,
    CUSTOMER_REFUND
  }

  private record Direction(Account debit, Account credit) {}

  private static Direction postingDirection(Settlement.Kind kind) {
    return switch (kind) {
      case PAYOUT -> new Direction(Account.ESCROW, Account.VENDOR_PAYABLE);
      case CLAWBACK -> new Direction(Account.ESCROW, Account.CUSTOMER_REFUND);
      case NONE -> throw new IllegalArgumentException("NONE has no posting");
    };
  }

  public record Posting(
      String idempotencyKey,
      Account debit,
      Account credit,
      long amountMinor,
      String currency,
      String verdictId,
      String outcomeId,
      Instant postedAt) {
    public Posting {
      if (idempotencyKey == null || idempotencyKey.isBlank()) {
        throw new IllegalArgumentException("idempotencyKey is required");
      }
      if (debit == null || credit == null) {
        throw new IllegalArgumentException("accounts are required");
      }
      if (debit == credit) {
        throw new IllegalArgumentException("accounts must differ");
      }
      if (amountMinor < 0) {
        throw new IllegalArgumentException("amount cannot be negative");
      }
      if (currency == null || currency.isBlank()) {
        throw new IllegalArgumentException("currency is required");
      }
      if (postedAt == null) {
        throw new IllegalArgumentException("postedAt is required");
      }
    }

    public Account fromAccount() {
      return debit;
    }

    public Account toAccount() {
      return credit;
    }
  }

  public static String idempotencyKey(
      String outcomeId, String evidenceHeadHash, String ruleVersion) {
    return sha256Hex(
        (outcomeId == null ? "" : outcomeId)
            + "|"
            + (evidenceHeadHash == null ? "" : evidenceHeadHash)
            + "|"
            + (ruleVersion == null ? "" : ruleVersion));
  }

  private static String sha256Hex(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder();
      for (byte valueByte : bytes) {
        builder.append(String.format("%02x", valueByte));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required", e);
    }
  }
}
