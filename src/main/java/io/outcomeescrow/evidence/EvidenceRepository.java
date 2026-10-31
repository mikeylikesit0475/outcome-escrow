package io.outcomeescrow.evidence;

import java.util.List;

public interface EvidenceRepository {
  void append(EvidenceRecord record);

  List<EvidenceRecord> findByOutcome(String outcomeId);

  List<EvidenceRecord> findByContract(String contractId);

  VerificationResult verify(String contractId);

  record VerificationResult(boolean valid, int length) {}
}
