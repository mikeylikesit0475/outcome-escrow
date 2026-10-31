package io.outcomeescrow.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public final class Evaluator {
  public static final String VERSION = "eval-0.1.0";

  private Evaluator() {}

  public static Verdict evaluate(Outcome outcome, List<Evidence> evidence, Instant now) {
    var cleanEvidence = evidence == null ? List.<Evidence>of() : List.copyOf(evidence);
    if (cleanEvidence.isEmpty()) {
      return new Verdict(
          outcome.id(),
          0,
          Verdict.Status.INSUFFICIENT_EVIDENCE,
          ruleVersion(outcome.ruleSpec()),
          sha256Hex(""));
    }

    var ordered =
        cleanEvidence.stream()
            .sorted(Comparator.comparing(Evidence::capturedAt).thenComparing(Evidence::hash))
            .toList();
    int score = scoreFor(outcome, ordered, now);
    var status =
        score >= 100
            ? Verdict.Status.MET
            : score <= 0 ? Verdict.Status.MISSED : Verdict.Status.PARTIAL;

    return new Verdict(
        outcome.id(),
        score,
        status,
        ruleVersion(outcome.ruleSpec()),
        ordered.get(ordered.size() - 1).hash());
  }

  private static int scoreFor(Outcome outcome, List<Evidence> evidence, Instant now) {
    String canonical = evidence.stream().map(Evidence::payload).collect(Collectors.joining(","));
    if (canonical.contains("late=true")) {
      return 0;
    }
    if (canonical.contains("success=true")) {
      return 100;
    }
    if (canonical.contains("success=false")) {
      return 0;
    }
    if (outcome.ruleSpec() instanceof RuleSpec.Deadline deadline && now.isAfter(deadline.dueAt())) {
      return 0;
    }
    return 50;
  }

  public static String ruleVersion(RuleSpec ruleSpec) {
    return sha256Hex(canonical(ruleSpec) + VERSION);
  }

  private static String canonical(RuleSpec ruleSpec) {
    return switch (ruleSpec) {
      case RuleSpec.Threshold threshold ->
          "Threshold:" + threshold.metric() + threshold.operator() + threshold.target();
      case RuleSpec.WindowedRate rate ->
          "WindowedRate:"
              + rate.numeratorMetric()
              + rate.denominatorMetric()
              + rate.operator()
              + rate.target();
      case RuleSpec.Deadline deadline -> "Deadline:" + deadline.eventMetric() + deadline.dueAt();
    };
  }

  private static String sha256Hex(String value) {
    try {
      var digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      var builder = new StringBuilder();
      for (byte valueByte : bytes) {
        builder.append(String.format("%02x", valueByte));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required", e);
    }
  }
}
