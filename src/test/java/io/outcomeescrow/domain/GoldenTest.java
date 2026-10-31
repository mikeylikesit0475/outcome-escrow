package io.outcomeescrow.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoldenTest {

  static List<String> cases() {
    return List.of("met-case.json", "missed-case.json", "insufficient-case.json");
  }

  @ParameterizedTest
  @MethodSource("cases")
  void goldenVerdict(String fileName) throws Exception {
    String json = read(fileName);
    String outcomeId = extract(json, "outcomeId");
    String payload = extract(json, "evidencePayload");
    String expectedStatus = extract(json, "expectedStatus");

    Outcome outcome =
        new Outcome(
            outcomeId,
            outcomeId,
            new RuleSpec.Threshold("availability", RuleSpec.Operator.AT_LEAST, 99.0),
            1000L,
            "USD");

    List<Evidence> evidence = new ArrayList<>();
    if (!payload.isBlank()) {
      String hash = sha256(payload);
      evidence.add(
          new Evidence(
              "fixture",
              "fixture-ref",
              Instant.parse("2026-01-01T00:00:00Z"),
              payload,
              hash,
              null));
    }

    Verdict first = Evaluator.evaluate(outcome, evidence, Instant.now());
    Verdict second = Evaluator.evaluate(outcome, evidence, Instant.now());
    assertEquals(expectedStatus, first.status().name());
    assertEquals(first, second);
  }

  @Test
  void shuffledEvidenceDoesNotChangeVerdict() throws Exception {
    Outcome outcome =
        new Outcome(
            "outcome-shuffle",
            "swap",
            new RuleSpec.Threshold("availability", RuleSpec.Operator.AT_LEAST, 99.0),
            1000L,
            "USD");
    List<Evidence> original = List.of(evidence("success=true"), evidence("success=false"));
    Verdict expected = Evaluator.evaluate(outcome, original, Instant.now());
    List<Evidence> reversed = new ArrayList<>(original.reversed());
    assertEquals(expected, Evaluator.evaluate(outcome, reversed, Instant.now()));
  }

  private static Evidence evidence(String payload) {
    return new Evidence(
        "fixture",
        "fixture-ref",
        Instant.parse("2026-01-01T00:00:00Z"),
        payload,
        sha256(payload),
        null);
  }

  private static String read(String fileName) throws Exception {
    try (InputStream stream = GoldenTest.class.getResourceAsStream("/golden/" + fileName)) {
      if (stream == null) {
        throw new IllegalStateException("missing resource: " + fileName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String extract(String json, String field) {
    Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
    if (!matcher.find()) {
      throw new IllegalStateException("field missing: " + field);
    }
    return matcher.group(1);
  }

  private static String sha256(String value) {
    try {
      var digest = java.security.MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder();
      for (byte valueByte : bytes) {
        builder.append(String.format("%02x", valueByte));
      }
      return builder.toString();
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
