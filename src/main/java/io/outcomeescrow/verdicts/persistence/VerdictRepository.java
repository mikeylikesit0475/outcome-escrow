package io.outcomeescrow.verdicts.persistence;

import java.util.List;

public interface VerdictRepository {
  void save(VerdictRecord record);

  VerdictRecord findById(String id);

  List<VerdictRecord> findByOutcome(String outcomeId);

  List<VerdictRecord> findByContract(String contractId);
}
