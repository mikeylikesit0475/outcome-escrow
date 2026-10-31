package io.outcomeescrow.domain;

import java.time.Instant;

public record Window(Instant from, Instant to) {
  public Window {
    if (from == null || to == null) {
      throw new IllegalArgumentException("window bounds are required");
    }
    if (from.isAfter(to)) {
      throw new IllegalArgumentException("invalid window");
    }
  }

  public static Window trailingDays(Instant now, int days) {
    return new Window(now.minusSeconds((long) days * 86400L), now);
  }
}
