package io.outcomeescrow.services;

import io.outcomeescrow.domain.Evidence;
import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.RuleSpec;
import io.outcomeescrow.domain.Settlement;
import io.outcomeescrow.domain.TierTable;
import io.outcomeescrow.domain.Verdict;
import io.outcomeescrow.domain.Window;
import io.outcomeescrow.evidence.EvidenceRecord;
import io.outcomeescrow.evidence.EvidenceRepository;
import io.outcomeescrow.ledger.persistence.JdbcLedgerPostingRepository;
import io.outcomeescrow.ledger.persistence.LedgerPostingRecord;
import io.outcomeescrow.seeds.OutcomeJson;
import io.outcomeescrow.verdicts.persistence.JdbcVerdictRepository;
import io.outcomeescrow.verdicts.persistence.VerdictRecord;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvaluationService {
  private final JdbcClient jdbcClient;
  private final EvidenceRepository evidenceRepository;
  private final JdbcVerdictRepository verdictRepository;
  private final JdbcLedgerPostingRepository postingRepository;
  private final WebhookService webhookService;
  private final Clock clock;

  public EvaluationService(
      JdbcClient jdbcClient,
      EvidenceRepository evidenceRepository,
      JdbcVerdictRepository verdictRepository,
      JdbcLedgerPostingRepository postingRepository,
      WebhookService webhookService,
      Clock clock) {
    this.jdbcClient = jdbcClient;
    this.evidenceRepository = evidenceRepository;
    this.verdictRepository = verdictRepository;
    this.postingRepository = postingRepository;
    this.webhookService = webhookService;
    this.clock = clock;
  }

  @Transactional
  public Map<String, Object> evaluate(String contractId) {
    Instant now = clock.instant();
    List<OutcomeRow> outcomes = loadOutcomes(contractId);
    List<Map<String, Object>> results = new ArrayList<>();
    for (OutcomeRow row : outcomes) {
      RuleSpec rule = OutcomeJson.parseRule(row.ruleJson());
      TierTable tiers = OutcomeJson.parseTiers(row.tiersJson());
      Map<String, String> params = parseParams(row.params());
      int days = row.windowDays() == null ? 30 : row.windowDays();
      Window window = Window.trailingDays(now, days);
      Outcome outcome =
          new Outcome(
              row.id(),
              row.name(),
              rule,
              tiers,
              row.value(),
              row.currency(),
              row.connector(),
              row.metric(),
              params,
              window);
      List<EvidenceRecord> records = evidenceRepository.findByOutcome(row.id());
      List<Evidence> evidence =
          records.stream()
              .map(
                  record ->
                      new Evidence(
                          record.id(),
                          record.contractId(),
                          record.outcomeId(),
                          record.source(),
                          record.sourceRef(),
                          record.capturedAt(),
                          record.payload(),
                          record.hash(),
                          record.prevHash(),
                          record.synthetic()))
              .toList();
      Verdict verdict = io.outcomeescrow.domain.Evaluator.evaluate(outcome, evidence, now, tiers);
      String verdictId = UUID.randomUUID().toString();
      verdictRepository.save(VerdictRecord.from(verdictId, verdict, evidence.size()));
      Settlement settlement = Settlement.from(outcome, verdict, tiers, now);
      boolean posted = false;
      if (settlement.kind() != Settlement.Kind.NONE) {
        String credit =
            settlement.kind() == Settlement.Kind.PAYOUT ? "VENDOR_PAYABLE" : "CUSTOMER_REFUND";
        String idempotencyKey =
            io.outcomeescrow.domain.ledger.Ledger.idempotencyKey(
                settlement.outcomeId(), settlement.evidenceHeadHash(), settlement.ruleVersion());
        posted =
            postingRepository.append(
                new LedgerPostingRecord(
                    UUID.randomUUID().toString(),
                    idempotencyKey,
                    "ESCROW",
                    credit,
                    settlement.amount().amountMinor(),
                    settlement.currency(),
                    now,
                    verdictId,
                    outcome.id()));
        if (posted) {
          Map<String, Object> event = new LinkedHashMap<>();
          event.put("type", "SETTLEMENT_POSTED");
          event.put("contractId", contractId);
          event.put("outcomeId", outcome.id());
          event.put("verdictId", verdictId);
          event.put("kind", settlement.kind().name());
          event.put("amountMinor", settlement.amount().amountMinor());
          event.put("currency", settlement.currency());
          event.put("idempotencyKey", idempotencyKey);
          event.put("evidenceHeadHash", settlement.evidenceHeadHash());
          event.put("ruleVersion", settlement.ruleVersion());
          webhookService.send(event);
        }
      }
      results.add(
          Map.of(
              "outcomeId", outcome.id(),
              "verdictId", verdictId,
              "status", verdict.status().name(),
              "score", verdict.score(),
              "settlementKind", settlement.kind().name(),
              "posted", posted));
    }
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("contractId", contractId);
    result.put("evaluatedAt", now.toString());
    result.put("verdicts", results);
    return result;
  }

  @Transactional
  public Map<String, Object> replay(String verdictId) {
    VerdictRecord stored = verdictRepository.findById(verdictId);
    if (stored == null) {
      throw new IllegalArgumentException("unknown verdict: " + verdictId);
    }
    List<OutcomeRow> outcomes = loadOutcomesForOutcome(stored.outcomeId());
    if (outcomes.isEmpty()) {
      throw new IllegalArgumentException("unknown outcome: " + stored.outcomeId());
    }
    OutcomeRow row = outcomes.get(0);
    RuleSpec rule = OutcomeJson.parseRule(row.ruleJson());
    TierTable tiers = OutcomeJson.parseTiers(row.tiersJson());
    Map<String, String> params = parseParams(row.params());
    Instant now = stored.evaluatedAt();
    int days = row.windowDays() == null ? 30 : row.windowDays();
    Window window = Window.trailingDays(now, days);
    Outcome outcome =
        new Outcome(
            row.id(),
            row.name(),
            rule,
            tiers,
            row.value(),
            row.currency(),
            row.connector(),
            row.metric(),
            params,
            window);
    List<EvidenceRecord> outcomeRecords = evidenceRepository.findByOutcome(row.id());
    java.util.List<EvidenceRecord> orderedRecords =
        outcomeRecords.stream()
            .sorted(
                java.util.Comparator.comparing(EvidenceRecord::capturedAt)
                    .thenComparing(r -> r.hash() == null ? "" : r.hash()))
            .toList();
    int limit = Math.max(0, stored.evidenceCount());
    if (limit > orderedRecords.size()) {
      limit = orderedRecords.size();
    }
    java.util.List<EvidenceRecord> selected =
        new java.util.ArrayList<>(orderedRecords.subList(0, limit));
    if (stored.evidenceHeadHash() != null && !stored.evidenceHeadHash().isBlank()) {
      // Ensure the selected set ends at the verdict's head hash when present.
      for (int i = selected.size(); i < orderedRecords.size(); i++) {
        selected.add(orderedRecords.get(i));
        if (java.util.Objects.equals(orderedRecords.get(i).hash(), stored.evidenceHeadHash())) {
          break;
        }
      }
      // If the head hash is not found, revert to the evidence count subset.
      if (!selected.isEmpty()
          && !java.util.Objects.equals(
              selected.get(selected.size() - 1).hash(), stored.evidenceHeadHash())) {
        selected =
            new java.util.ArrayList<>(
                orderedRecords.subList(0, Math.min(limit, orderedRecords.size())));
      }
    }
    List<Evidence> evidence =
        selected.stream()
            .map(
                record ->
                    new Evidence(
                        record.id(),
                        record.contractId(),
                        record.outcomeId(),
                        record.source(),
                        record.sourceRef(),
                        record.capturedAt(),
                        record.payload(),
                        record.hash(),
                        record.prevHash(),
                        record.synthetic()))
            .toList();
    Verdict replayed = io.outcomeescrow.domain.Evaluator.evaluate(outcome, evidence, now, tiers);
    boolean identical =
        stored.score() == replayed.score()
            && stored.status().equals(replayed.status().name())
            && java.util.Objects.equals(stored.ruleVersion(), replayed.ruleVersion())
            && java.util.Objects.equals(stored.evidenceHeadHash(), replayed.evidenceHeadHash());

    Map<String, Object> storedMap = new LinkedHashMap<>();
    storedMap.put("verdictId", stored.id());
    storedMap.put("outcomeId", stored.outcomeId());
    storedMap.put("score", stored.score());
    storedMap.put("status", stored.status());
    storedMap.put("ruleVersion", stored.ruleVersion());
    storedMap.put("evidenceHeadHash", stored.evidenceHeadHash());

    Map<String, Object> replayedMap = new LinkedHashMap<>();
    replayedMap.put("outcomeId", replayed.outcomeId());
    replayedMap.put("score", replayed.score());
    replayedMap.put("status", replayed.status().name());
    replayedMap.put("ruleVersion", replayed.ruleVersion());
    replayedMap.put("evidenceHeadHash", replayed.evidenceHeadHash());
    replayedMap.put("evidenceCount", replayed.evidenceCount());

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("identical", identical);
    result.put("stored", storedMap);
    result.put("replayed", replayedMap);
    return result;
  }

  List<OutcomeRow> loadOutcomes(String contractId) {
    return jdbcClient
        .sql(
            """
            select id, name, connector, metric, window_days, params, milestone_minor, currency, rule_json, tiers_json
            from outcomes where contract_id = :contractId order by id
            """)
        .param("contractId", contractId)
        .query(rowMapper())
        .list();
  }

  List<OutcomeRow> loadOutcomesForOutcome(String outcomeId) {
    return jdbcClient
        .sql(
            """
            select id, name, connector, metric, window_days, params, milestone_minor, currency, rule_json, tiers_json
            from outcomes where id = :outcomeId
            """)
        .param("outcomeId", outcomeId)
        .query(rowMapper())
        .list();
  }

  private org.springframework.jdbc.core.RowMapper<OutcomeRow> rowMapper() {
    return (rs, row) ->
        new OutcomeRow(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("connector"),
            rs.getString("metric"),
            rs.getObject("window_days", Integer.class),
            rs.getString("params"),
            rs.getLong("milestone_minor"),
            rs.getString("currency"),
            rs.getString("rule_json"),
            rs.getString("tiers_json"));
  }

  static Map<String, String> parseParams(String text) {
    if (text == null || text.isBlank()) {
      return Map.of();
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      return mapper.readValue(
          text, new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    } catch (Exception ignored) {
      return Map.of();
    }
  }

  public record OutcomeRow(
      String id,
      String name,
      String connector,
      String metric,
      Integer windowDays,
      String params,
      long value,
      String currency,
      String ruleJson,
      String tiersJson) {}
}
