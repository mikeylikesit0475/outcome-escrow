package io.outcomeescrow.domain;

public record Outcome(
    String id, String name, RuleSpec ruleSpec, long milestoneValueMinor, String currency) {
  public Outcome {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
  }
}
