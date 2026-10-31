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
    return evaluate(outcome, evidence, now, defaultTierTable());
  }

  public static Verdict evaluate(
      Outcome outcome, List<Evidence> evidence, Instant now, TierTable tierTable) {
    var cleanEvidence = evidence == null ? List.<Evidence>of() : List.copyOf(evidence);
    if (outcome == null) {
      throw new IllegalArgumentException("outcome is required");
    }
    if (now == null) {
      throw new IllegalArgumentException("now is required");
    }
    if (tierTable == null) {
      throw new IllegalArgumentException("tierTable is required");
    }

    var ordered =
        cleanEvidence.stream()
            .sorted(Comparator.comparing(Evidence::capturedAt).thenComparing(Evidence::hash))
            .toList();

    int score;
    String explanation;
    boolean insufficient;
    String headHash = ordered.isEmpty() ? "" : ordered.get(ordered.size() - 1).hash();

    if (cleanEvidence.isEmpty()) {
      score = 0;
      insufficient = true;
      explanation = "INSUFFICIENT_EVIDENCE: no evidence";
    } else {
      ScoreResult result = scoreFor(outcome, ordered, now);
      score = result.score;
      insufficient = result.insufficient;
      explanation = result.explanation;
    }

    var status =
        insufficient
            ? Verdict.Status.INSUFFICIENT_EVIDENCE
            : score >= 10_000
                ? Verdict.Status.MET
                : score <= 0 ? Verdict.Status.MISSED : Verdict.Status.PARTIAL;

    return new Verdict(
        outcome.id(),
        score,
        status,
        tierTable.tierForScore(score),
        now,
        ordered.size(),
        ruleVersion(outcome.ruleSpec()),
        headHash,
        explanation);
  }

  private static TierTable defaultTierTable() {
    return new TierTable(
        8_000,
        2_000,
        List.of(
            new TierTable.Tier(0, 0),
            new TierTable.Tier(8_000, 5_000),
            new TierTable.Tier(9_000, 10_000)));
  }

  private static final class ScoreResult {
    int score;
    boolean insufficient;
    String explanation;
  }

  private static ScoreResult scoreFor(Outcome outcome, List<Evidence> ordered, Instant now) {
    var result = new ScoreResult();
    RuleSpec rule = outcome.ruleSpec();
    switch (rule) {
      case RuleSpec.Threshold threshold -> {
        Double latestValue = null;
        Instant latestCaptured = null;
        String latestHash = null;
        int validCount = 0;
        for (Evidence evidence : ordered) {
          try {
            var payload = CanonicalJson.parseObject(evidence.payload());
            CanonicalJson.requireExactKeys(payload, "value");
            double value = CanonicalJson.number(payload, "value");
            validCount++;
            boolean replace =
                latestCaptured == null
                    || evidence.capturedAt().isAfter(latestCaptured)
                    || (evidence.capturedAt().equals(latestCaptured)
                        && evidence.hash().compareToIgnoreCase(latestHash) < 0);
            if (latestCaptured == null || replace) {
              latestValue = value;
              latestCaptured = evidence.capturedAt();
              latestHash = evidence.hash();
            }
          } catch (IllegalArgumentException ignored) {
          }
        }
        if (validCount == 0) {
          result.score = 0;
          result.insufficient = true;
          result.explanation = "INSUFFICIENT_EVIDENCE: no valid threshold payload";
          return result;
        }
        if (validCount < ordered.size()) {
          result.score = 0;
          result.insufficient = true;
          result.explanation = "INSUFFICIENT_EVIDENCE: malformed evidence payload";
          return result;
        }
        result.insufficient = false;
        result.score = thresholdScore(threshold, latestValue);
        result.explanation =
            "threshold "
                + threshold.metric()
                + " "
                + latestValue
                + " "
                + threshold.operator()
                + " "
                + threshold.target()
                + " => "
                + result.score
                + " bps";
      }
      case RuleSpec.WindowedRate rate -> {
        int validCount = 0;
        double numeratorTotal = 0;
        double denominatorTotal = 0;
        for (Evidence evidence : ordered) {
          try {
            var payload = CanonicalJson.parseObject(evidence.payload());
            CanonicalJson.requireExactKeys(payload, "numerator", "denominator");
            var numerator = CanonicalJson.number(payload, "numerator");
            var denominator = CanonicalJson.number(payload, "denominator");
            validCount++;
            numeratorTotal += numerator;
            denominatorTotal += denominator;
          } catch (IllegalArgumentException ignored) {
          }
        }
        if (validCount == 0) {
          result.score = 0;
          result.insufficient = true;
          result.explanation = "INSUFFICIENT_EVIDENCE: no valid rate payload";
          return result;
        }
        if (validCount < ordered.size()) {
          result.score = 0;
          result.insufficient = true;
          result.explanation = "INSUFFICIENT_EVIDENCE: malformed evidence payload";
          return result;
        }
        result.insufficient = false;
        if (denominatorTotal == 0) {
          result.score = 0;
          result.explanation = "INSUFFICIENT_EVIDENCE: denominator is zero";
          result.insufficient = true;
          return result;
        }
        double ratio = numeratorTotal / denominatorTotal;
        int baseScore = clamp((int) Math.round(ratio * 10_000));
        boolean holds =
            switch (rate.operator()) {
              case AT_LEAST -> ratio >= rate.target();
              case AT_MOST -> ratio <= rate.target();
              case EXACTLY -> ratio == rate.target();
            };
        result.score = holds ? 10_000 : baseScore;
        result.explanation =
            "windowed rate numerator="
                + numeratorTotal
                + " denominator="
                + denominatorTotal
                + " "
                + rate.operator()
                + " target="
                + rate.target()
                + " => "
                + result.score
                + " bps";
      }
      case RuleSpec.Deadline deadline -> {
        boolean met = false;
        boolean missed = false;
        int validCount = 0;
        Instant eventAt = null;
        for (Evidence evidence : ordered) {
          try {
            var payload = CanonicalJson.parseObject(evidence.payload());
            CanonicalJson.requireExactKeys(payload, "eventAt");
            var parsedEvent = Instant.parse(CanonicalJson.string(payload, "eventAt"));
            validCount++;
            if (eventAt == null || !parsedEvent.isAfter(eventAt)) {
              eventAt = parsedEvent;
            }
            if (!parsedEvent.isAfter(deadline.dueAt())) {
              met = true;
            } else {
              missed = true;
            }
          } catch (IllegalArgumentException ignored) {
          }
        }
        if (validCount == 0) {
          if (!now.isBefore(deadline.dueAt())) {
            result.score = 0;
            result.insufficient = false;
            result.explanation = "deadline missed: no event by due time";
          } else {
            result.score = 0;
            result.insufficient = true;
            result.explanation = "INSUFFICIENT_EVIDENCE: no event yet";
          }
          return result;
        }
        if (validCount < ordered.size()) {
          result.score = 0;
          result.insufficient = true;
          result.explanation = "INSUFFICIENT_EVIDENCE: malformed evidence payload";
          return result;
        }
        result.insufficient = false;
        result.score = met ? 10_000 : 0;
        result.explanation =
            "deadline eventAt="
                + eventAt
                + " dueAt="
                + deadline.dueAt()
                + " => "
                + (met ? "MET" : "MISSED");
      }
    }
    return result;
  }

  private static int thresholdScore(RuleSpec.Threshold threshold, double value) {
    double target = threshold.target();
    double normalized = 0;
    switch (threshold.operator()) {
      case AT_LEAST ->
          normalized = target > 0 ? value / target : target == 0 ? 1.0 : (value >= 0 ? 1.0 : 0.0);
      case AT_MOST -> normalized = value > 0 ? target / value : (target >= 0 ? 1.0 : 0.0);
      case EXACTLY -> {
        if (value == target) {
          return 10_000;
        }
        double base = Math.max(1.0, Math.abs(target));
        normalized = 1.0 - Math.min(1.0, Math.abs(value - target) / base);
      }
    }
    if (target < 0) {
      // Clamp thresholds should remain operationally bounded to 0..1.
      normalized = Math.max(0.0, Math.min(1.0, normalized));
    } else {
      normalized = Math.max(0.0, Math.min(1.0, normalized));
    }

    // If threshold condition holds, ensure perfect score.
    boolean holds =
        switch (threshold.operator()) {
          case AT_LEAST -> value >= target;
          case AT_MOST -> value <= target;
          case EXACTLY -> value == target;
        };
    if (holds) {
      return 10_000;
    }
    return (int) Math.round(normalized * 10_000);
  }

  private static int clamp(int value) {
    return Math.max(0, Math.min(10_000, value));
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
