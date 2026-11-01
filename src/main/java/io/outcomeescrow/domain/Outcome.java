package io.outcomeescrow.domain;

import java.util.Map;

public record Outcome(
    String id,
    String name,
    RuleSpec ruleSpec,
    TierTable tiers,
    long milestoneValueMinor,
    String currency,
    String connector,
    String metric,
    Map<String, String> params,
    Window window) {
  public Outcome {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id is required");
    }
    if (params == null) {
      params = Map.of();
    }
    if (tiers == null) {
      tiers =
          new TierTable(
              8_000,
              2_000,
              java.util.List.of(
                  new TierTable.Tier(0, 0),
                  new TierTable.Tier(8_000, 5_000),
                  new TierTable.Tier(9_000, 10_000)));
    }
  }

  public Outcome(
      String id, String name, RuleSpec ruleSpec, long milestoneValueMinor, String currency) {
    this(id, name, ruleSpec, null, milestoneValueMinor, currency, null, null, Map.of(), null);
  }

  public Outcome(
      String id,
      String name,
      RuleSpec ruleSpec,
      TierTable tiers,
      long milestoneValueMinor,
      String currency) {
    this(id, name, ruleSpec, tiers, milestoneValueMinor, currency, null, null, Map.of(), null);
  }

  public Outcome(
      String id,
      String name,
      RuleSpec ruleSpec,
      long milestoneValueMinor,
      String currency,
      String connector,
      String metric,
      Map<String, String> params,
      Window window) {
    this(
        id, name, ruleSpec, null, milestoneValueMinor, currency, connector, metric, params, window);
  }

  public Window effectiveWindow(Window nowWindow) {
    return window != null ? window : nowWindow;
  }
}
