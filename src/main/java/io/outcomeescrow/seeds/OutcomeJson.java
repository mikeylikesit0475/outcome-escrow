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
      com.fasterxml.jackson.databind.JsonNode data =
          JSON.readTree(text == null || text.isBlank() ? "{}" : text);
      String type = data.path("type").asText("");
      return switch (type) {
        case "Threshold" ->
            new RuleSpec.Threshold(
                data.path("metric").asText(),
                RuleSpec.Operator.valueOf(data.path("operator").asText()),
                data.path("target").asDouble());
        case "WindowedRate" ->
            new RuleSpec.WindowedRate(
                data.path("numeratorMetric").asText(),
                data.path("denominatorMetric").asText(),
                RuleSpec.Operator.valueOf(data.path("operator").asText()),
                data.path("target").asDouble());
        case "Deadline" ->
            new RuleSpec.Deadline(
                data.path("eventMetric").asText(),
                java.time.Instant.parse(data.path("dueAt").asText()));
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
      com.fasterxml.jackson.databind.JsonNode data = JSON.readTree(text);
      if (data == null || data.isNull() || data.isMissingNode()) {
        return null;
      }
      int floor = data.path("floorScore").asInt(0);
      int clawback = data.path("clawbackBasisPointsBelowFloor").asInt(0);
      List<TierTable.Tier> parsed = new java.util.ArrayList<>();
      for (com.fasterxml.jackson.databind.JsonNode item : data.path("tiers")) {
        parsed.add(
            new TierTable.Tier(
                item.path("minimumScore").asInt(0), item.path("payoutBasisPoints").asInt(0)));
      }
      return new TierTable(floor, clawback, parsed);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
