package io.outcomeescrow.domain;

import java.time.Instant;
import java.util.List;

public record Contract(
    String id,
    String name,
    java.util.Currency currency,
    List<Outcome> outcomes,
    Instant createdAt) {
  public Contract {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    if (outcomes == null) {
      throw new IllegalArgumentException("outcomes is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    outcomes = List.copyOf(outcomes);
  }
}
