package io.outcomeescrow.domain;

import java.time.Instant;

public sealed interface RuleSpec
    permits RuleSpec.Threshold, RuleSpec.WindowedRate, RuleSpec.Deadline {

  record Threshold(String metric, Operator operator, double target) implements RuleSpec {
    public Threshold {
      if (operator == null) {
        throw new IllegalArgumentException("operator is required");
      }
    }
  }

  record WindowedRate(
      String numeratorMetric, String denominatorMetric, Operator operator, double target)
      implements RuleSpec {
    public WindowedRate {
      if (operator == null) {
        throw new IllegalArgumentException("operator is required");
      }
    }
  }

  record Deadline(String eventMetric, Instant dueAt) implements RuleSpec {
    public Deadline {
      if (dueAt == null) {
        throw new IllegalArgumentException("dueAt is required");
      }
    }
  }

  enum Operator {
    AT_LEAST,
    AT_MOST,
    EXACTLY
  }
}
