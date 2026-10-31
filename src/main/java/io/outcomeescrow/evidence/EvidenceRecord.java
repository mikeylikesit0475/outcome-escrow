package io.outcomeescrow.evidence;

import java.time.Instant;

public record EvidenceRecord(
    String id,
    String contractId,
    String outcomeId,
    String source,
    String sourceRef,
    Instant capturedAt,
    String payload,
    String hash,
    String prevHash) {}
