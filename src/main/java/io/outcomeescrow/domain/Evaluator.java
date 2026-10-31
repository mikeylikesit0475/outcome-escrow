package io.outcomeescrow.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

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
    // Evidence payloads are untrusted input. Recognize only whole, exact status
    // facts so a substring embedded in a metric value or free-form text cannot
    // choose the verdict.
    boolean late = false;
    boolean succeeded = false;
    boolean failed = false;
    for (Evidence record : evidence) {
      if (record.payload() == null) {
        continue;
      }
      if (record.payload().equals("late=true")) {
        late = true;
      } else if (record.payload().equals("success=true")) {
        succeeded = true;
      } else if (record.payload().equals("success=false")) {
        failed = true;
      }
    }
    if (late) {
      return 0;
    }
    if (succeeded) {
      return 100;
    }
    if (failed) {
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
