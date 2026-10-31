package io.outcomeescrow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static Stream<String> goldenFiles() {
    return List.of(
        "deadline-met.json",
        "deadline-missed.json",
        "deadline-missing.json",
        "malformed-threshold-payload.json",
        "threshold-at-least-met.json",
        "threshold-at-least-missed.json",
        "threshold-at-least-partial.json",
        "threshold-at-most-met.json",
        "threshold-exact-partial.json",
        "threshold-latest-by-hash.json",
        "windowed-rate-insufficient-zero.json",
        "windowed-rate-met.json",
        "windowed-rate-partial.json")
        .stream();
  }

  @ParameterizedTest
  @MethodSource("goldenFiles")
  void goldenVerdictIsDeterministic(String fileName) throws Exception {
    GoldenCase testCase = read(fileName);
    RuleSpec rule = toRuleSpec(testCase.rule());
    Outcome outcome =
        new Outcome(
            testCase.outcomeId(),
            testCase.outcomeName(),
            rule,
            testCase.milestoneValueMinor(),
            testCase.currency());
    List<Evidence> evidence = toEvidence(testCase.evidence());
    Instant now = Instant.parse(testCase.now());

    Verdict first = Evaluator.evaluate(outcome, evidence, now);
    Verdict second = Evaluator.evaluate(outcome, evidence, now);

    assertEquals(testCase.expectedStatus(), first.status().name());
    assertEquals(testCase.expectedScore(), first.score());
    assertEquals(first, second);
  }

  @Test
  void shuffledWindowedRateEvidenceDoesNotChangeVerdict() throws Exception {
    GoldenCase testCase = read("windowed-rate-met.json");
    RuleSpec rule = toRuleSpec(testCase.rule());
    Outcome outcome =
        new Outcome(
            testCase.outcomeId(),
            testCase.outcomeName(),
            rule,
            testCase.milestoneValueMinor(),
            testCase.currency());
    List<Evidence> original = toEvidence(testCase.evidence());
    Instant now = Instant.parse(testCase.now());
    Verdict expected = Evaluator.evaluate(outcome, original, now);

    List<Evidence> reversed = new ArrayList<>(original.reversed());
    assertEquals(expected, Evaluator.evaluate(outcome, reversed, now));

    if (original.size() > 1) {
      List<Evidence> shuffled = List.of(original.get(1), original.get(0));
      assertEquals(expected, Evaluator.evaluate(outcome, shuffled, now));
    }
  }

  private static GoldenCase read(String fileName) throws Exception {
    try (InputStream stream = GoldenTest.class.getResourceAsStream("/golden/" + fileName)) {
      if (stream == null) {
        throw new IllegalStateException("missing golden resource: " + fileName);
      }
      return MAPPER.readValue(stream, GoldenCase.class);
    }
  }

  private static RuleSpec toRuleSpec(JsonNode node) {
    String type = node.get("type").asText();
    if ("Threshold".equals(type)) {
      return new RuleSpec.Threshold(
          node.get("metric").asText(),
          RuleSpec.Operator.valueOf(node.get("operator").asText()),
          node.get("target").asDouble());
    }
    if ("WindowedRate".equals(type)) {
      return new RuleSpec.WindowedRate(
          node.get("numeratorMetric").asText(),
          node.get("denominatorMetric").asText(),
          RuleSpec.Operator.valueOf(node.get("operator").asText()),
          node.get("target").asDouble());
    }
    if ("Deadline".equals(type)) {
      return new RuleSpec.Deadline(
          node.get("eventMetric").asText(), Instant.parse(node.get("dueAt").asText()));
    }
    throw new IllegalArgumentException("unknown rule type: " + type);
  }

  private static List<Evidence> toEvidence(List<GoldenEvidence> goldenEvidence) {
    List<Evidence> result = new ArrayList<>();
    for (GoldenEvidence item : goldenEvidence) {
      try {
        String payload = MAPPER.writeValueAsString(item.payload());
        result.add(
            new Evidence(
                item.source(),
                item.sourceRef(),
                Instant.parse(item.capturedAt()),
                payload,
                item.hash(),
                null));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    return result;
  }

  private record GoldenCase(
      String outcomeId,
      String outcomeName,
      long milestoneValueMinor,
      String currency,
      JsonNode rule,
      List<GoldenEvidence> evidence,
      String now,
      String expectedStatus,
      int expectedScore) {}

  private record GoldenEvidence(
      String source,
      String sourceRef,
      String capturedAt,
      Map<String, Object> payload,
      String hash) {}
}
