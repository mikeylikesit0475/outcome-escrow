package io.outcomeescrow.domain;

import java.util.List;
import java.util.Map;

public record Outcome(
    String id,
    String name,
    RuleSpec ruleSpec,
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
  }

  public Outcome(String id, String name, RuleSpec ruleSpec, long milestoneValueMinor, String currency) {
    this(id, name, ruleSpec, milestoneValueMinor, currency, null, null, Map.of(), null);
  }

  public Window effectiveWindow(Window nowWindow) {
    return window != null ? window : nowWindow;
  }
}
