package io.outcomeescrow.evidence;

import io.outcomeescrow.domain.Evidence;
import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.Window;
import java.time.Instant;
import java.util.List;

public interface EvidenceSource {
  String id();

  boolean synthetic();

  List<Evidence> fetch(Outcome outcome, Window window, Instant now);
}
