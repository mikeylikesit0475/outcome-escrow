package io.outcomeescrow.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.RuleSpec;
import io.outcomeescrow.domain.TierTable;
import io.outcomeescrow.domain.Window;
import io.outcomeescrow.evidence.EvidenceRecord;
import io.outcomeescrow.evidence.EvidenceRepository;
import io.outcomeescrow.evidence.EvidenceSource;
import io.outcomeescrow.evidence.EvidenceSourceRegistry;
import io.outcomeescrow.seeds.OutcomeJson;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EvidencePullController {
  private final JdbcClient jdbcClient;
  private final EvidenceSourceRegistry registry;
  private final EvidenceRepository evidenceRepository;
  private final ObjectMapper mapper = new ObjectMapper();

  public EvidencePullController(
      JdbcClient jdbcClient,
      EvidenceSourceRegistry registry,
      EvidenceRepository evidenceRepository) {
    this.jdbcClient = jdbcClient;
    this.registry = registry;
    this.evidenceRepository = evidenceRepository;
  }

  @PostMapping("/contracts/{contractId}/evidence:pull")
  public Map<String, Object> pull(@PathVariable String contractId) {
    List<OutcomeRow> outcomes =
        jdbcClient
            .sql(
                """
                select id, name, connector, metric, window_days, params, milestone_minor, rule_json, tiers_json
                from outcomes where contract_id = :contractId
                """)
            .param("contractId", contractId)
            .query(
                (rs, row) ->
                    new OutcomeRow(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("connector"),
                        rs.getString("metric"),
                        rs.getObject("window_days", Integer.class),
                        rs.getString("params"),
                        rs.getLong("milestone_minor"),
                        rs.getString("rule_json"),
                        rs.getString("tiers_json")))
            .list();
    int pulled = 0;
    Instant now = Instant.now();
    for (OutcomeRow outcome : outcomes) {
      EvidenceSource source =
          outcome.connector() == null ? null : registry.find(outcome.connector()).orElse(null);
      if (source == null) {
        continue;
      }
      int days = outcome.windowDays() == null ? 30 : outcome.windowDays();
      Window window = Window.trailingDays(now, days);
      Map<String, String> params = parseParams(outcome.params());
      RuleSpec rule = OutcomeJson.parseRule(outcome.ruleJson());
      TierTable tiers = OutcomeJson.parseTiers(outcome.tiersJson());
      Outcome domainOutcome =
          new Outcome(
              outcome.id(),
              outcome.name(),
              rule,
              tiers,
              outcome.value(),
              "USD",
              outcome.connector(),
              outcome.metric(),
              params,
              window);
      for (io.outcomeescrow.domain.Evidence evidence : source.fetch(domainOutcome, window, now)) {
        evidenceRepository.append(
            new EvidenceRecord(
                evidence.id() == null || evidence.id().isBlank()
                    ? java.util.UUID.randomUUID().toString()
                    : evidence.id(),
                contractId,
                outcome.id(),
                evidence.source(),
                evidence.sourceRef(),
                evidence.capturedAt(),
                evidence.payload(),
                evidence.hash(),
                null,
                evidence.synthetic()));
        pulled++;
      }
    }
    return Map.of("pulled", pulled);
  }

  private Map<String, String> parseParams(String text) {
    if (text == null || text.isBlank()) {
      return Map.of();
    }
    try {
      return mapper.readValue(text, new TypeReference<Map<String, String>>() {});
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  record OutcomeRow(
      String id,
      String name,
      String connector,
      String metric,
      Integer windowDays,
      String params,
      long value,
      String ruleJson,
      String tiersJson) {}
}
