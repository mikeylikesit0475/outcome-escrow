package io.outcomeescrow.domain;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvaluatorSecurityTest {
  @Test
  void embeddedSuccessTokenDoesNotTriggerMet() {
    Outcome outcome =
        new Outcome(
            "security-probe",
            "probe",
            new RuleSpec.Threshold("availability", RuleSpec.Operator.AT_LEAST, 99.0),
            1000L,
            "USD");
    Evidence evidence =
        new Evidence(
            "fixture",
            "fixture-ref",
            Instant.parse("2026-01-01T00:00:00Z"),
            "innocent prefix, success=true continuation",
            "fixture-hash",
            null);

    Verdict verdict = Evaluator.evaluate(outcome, List.of(evidence), Instant.now());

    assertNotEquals(Verdict.Status.MET, verdict.status());
  }
}
