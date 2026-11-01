package io.outcomeescrow.seeds;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.outcomeescrow.domain.RuleSpec;
import io.outcomeescrow.domain.TierTable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OutcomeJson {
  private static final ObjectMapper JSON = new ObjectMapper();

  private OutcomeJson() {}

  public static String ruleJson(RuleSpec ruleSpec) {
    if (ruleSpec == null) {
      return "{}";
    }
    Map<String, Object> data = new LinkedHashMap<>();
    switch (ruleSpec) {
      case RuleSpec.Threshold threshold -> {
        data.put("type", "Threshold");
        data.put("metric", threshold.metric());
        data.put("operator", threshold.operator().name());
        data.put("target", threshold.target());
      }
      case RuleSpec.WindowedRate rate -> {
        data.put("type", "WindowedRate");
        data.put("numeratorMetric", rate.numeratorMetric());
        data.put("denominatorMetric", rate.denominatorMetric());
        data.put("operator", rate.operator().name());
        data.put("target", rate.target());
      }
      case RuleSpec.Deadline deadline -> {
        data.put("type", "Deadline");
        data.put("eventMetric", deadline.eventMetric());
        data.put("dueAt", deadline.dueAt().toString());
      }
    }
    try {
      return JSON.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static RuleSpec parseRule(String text) {
    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> data = JSON.readValue(text == null ? "{}" : text, Map.class);
      String type = String.valueOf(data.getOrDefault("type", ""));
      return switch (type) {
        case "Threshold" ->
            new RuleSpec.Threshold(
                String.valueOf(data.get("metric")),
                RuleSpec.Operator.valueOf(String.valueOf(data.get("operator"))),
                ((Number) data.get("target")).doubleValue());
        case "WindowedRate" ->
            new RuleSpec.WindowedRate(
                String.valueOf(data.get("numeratorMetric")),
                String.valueOf(data.get("denominatorMetric")),
                RuleSpec.Operator.valueOf(String.valueOf(data.get("operator"))),
                ((Number) data.get("target")).doubleValue());
        case "Deadline" ->
            new RuleSpec.Deadline(
                String.valueOf(data.get("eventMetric")),
                java.time.Instant.parse(String.valueOf(data.get("dueAt"))));
        default -> null;
      };
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static String tierJson(TierTable tiers) {
    if (tiers == null) {
      return "{}";
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("floorScore", tiers.floorScore());
    data.put("clawbackBasisPointsBelowFloor", tiers.clawbackBasisPointsBelowFloor());
    List<Map<String, Object>> tierData = new java.util.ArrayList<>();
    for (TierTable.Tier tier : tiers.tiers()) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("minimumScore", tier.minimumScore());
      map.put("payoutBasisPoints", tier.payoutBasisPoints());
      tierData.add(map);
    }
    data.put("tiers", tierData);
    try {
      return JSON.writeValueAsString(data);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public static TierTable parseTiers(String text) {
    try {
      if (text == null || text.isBlank() || text.equals("{}")) {
        return null;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> data = JSON.readValue(text, Map.class);
      int floor = ((Number) data.getOrDefault("floorScore", 0)).intValue();
      int clawback = ((Number) data.getOrDefault("clawbackBasisPointsBelowFloor", 0)).intValue();
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> tiers =
          (List<Map<String, Object>>) data.getOrDefault("tiers", List.of());
      List<TierTable.Tier> parsed = new java.util.ArrayList<>();
      for (Map<String, Object> map : tiers) {
        parsed.add(
            new TierTable.Tier(
                ((Number) map.get("minimumScore")).intValue(),
                ((Number) map.get("payoutBasisPoints")).intValue()));
      }
      return new TierTable(floor, clawback, parsed);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
