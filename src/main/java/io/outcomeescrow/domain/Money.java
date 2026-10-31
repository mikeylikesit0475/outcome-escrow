package io.outcomeescrow.domain;

public record Money(long amountMinor, String currency) {
  public Money {
    if (currency == null || currency.isBlank()) {
      throw new IllegalArgumentException("currency is required");
    }
  }

  public static Money of(long amountMinor, String currency) {
    return new Money(amountMinor, currency);
  }
}
