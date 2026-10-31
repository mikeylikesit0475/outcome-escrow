package io.outcomeescrow.evidence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class EvidenceSourceRegistry {
  private final Map<String, EvidenceSource> sources = new ConcurrentHashMap<>();

  public void register(EvidenceSource source) {
    if (source == null) {
      throw new IllegalArgumentException("source is required");
    }
    if (sources.putIfAbsent(source.id(), source) != null) {
      throw new IllegalArgumentException("duplicate evidence source: " + source.id());
    }
  }

  public Optional<EvidenceSource> find(String id) {
    if (id == null || id.isBlank()) {
      return Optional.empty();
    }
    return Optional.ofNullable(sources.get(id));
  }

  public List<EvidenceSource> all() {
    return List.copyOf(sources.values());
  }
}
