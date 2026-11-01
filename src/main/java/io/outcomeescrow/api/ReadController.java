package io.outcomeescrow.api;

import io.outcomeescrow.verdicts.persistence.JdbcVerdictRepository;
import io.outcomeescrow.verdicts.persistence.VerdictRecord;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReadController {
  private final JdbcClient jdbcClient;
  private final JdbcVerdictRepository verdictRepository;

  public ReadController(JdbcClient jdbcClient, JdbcVerdictRepository verdictRepository) {
    this.jdbcClient = jdbcClient;
    this.verdictRepository = verdictRepository;
  }

  @GetMapping("/contracts")
  public List<Map<String, Object>> contracts() {
    return jdbcClient
        .sql("select id, name, currency, created_at from contracts order by id")
        .query(
            (rs, row) -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("id", rs.getString("id"));
              map.put("name", rs.getString("name"));
              map.put("currency", rs.getString("currency"));
              map.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
              return map;
            })
        .list();
  }

  @GetMapping("/contracts/{id}")
  public Map<String, Object> contract(@PathVariable String id) {
    List<Map<String, Object>> rows =
        jdbcClient
            .sql("select id, name, currency, created_at from contracts where id = :id")
            .param("id", id)
            .query(
                (rs, row) -> {
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("name", rs.getString("name"));
                  map.put("currency", rs.getString("currency"));
                  map.put("createdAt", rs.getTimestamp("created_at").toInstant().toString());
                  return map;
                })
            .list();
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("unknown contract: " + id);
    }
    return rows.get(0);
  }

  @GetMapping("/contracts/{id}/outcomes")
  public List<Map<String, Object>> outcomes(@PathVariable String id) {
    return jdbcClient
        .sql(
            """
            select id, name, connector, metric, window_days, milestone_minor, currency, rule_json, tiers_json
            from outcomes where contract_id = :id order by id
            """)
        .param("id", id)
        .query(
            (rs, row) -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("id", rs.getString("id"));
              map.put("name", rs.getString("name"));
              map.put("connector", rs.getString("connector"));
              map.put("metric", rs.getString("metric"));
              map.put("windowDays", rs.getObject("window_days", Integer.class));
              map.put("milestoneValueMinor", rs.getLong("milestone_minor"));
              map.put("currency", rs.getString("currency"));
              map.put("ruleJson", rs.getString("rule_json"));
              map.put("tiersJson", rs.getString("tiers_json"));
              return map;
            })
        .list();
  }

  @GetMapping("/contracts/{id}/verdicts")
  public List<VerdictRecord> verdicts(@PathVariable String id) {
    return verdictRepository.findByContract(id);
  }

  @GetMapping("/verdicts/{id}")
  public VerdictRecord verdict(@PathVariable String id) {
    VerdictRecord record = verdictRepository.findById(id);
    if (record == null) {
      throw new IllegalArgumentException("unknown verdict: " + id);
    }
    return record;
  }

  @GetMapping("/ledger/balances")
  public List<Map<String, Object>> ledgerBalances() {
    return jdbcClient
        .sql(
            """
            select a.id, a.currency,
              coalesce(sum(case when p.to_account_id = a.id then p.amount_minor else 0 end),0)
              - coalesce(sum(case when p.from_account_id = a.id then p.amount_minor else 0 end),0)
              as balance_minor
            from ledger_accounts a
            left join ledger_postings p on p.from_account_id = a.id or p.to_account_id = a.id
            group by a.id, a.currency
            order by a.id
            """)
        .query(
            (rs, row) -> {
              Map<String, Object> map = new LinkedHashMap<>();
              map.put("accountId", rs.getString("id"));
              map.put("currency", rs.getString("currency"));
              map.put("balanceMinor", rs.getLong("balance_minor"));
              return map;
            })
        .list();
  }

  @GetMapping(value = "/contracts/{id}/report", produces = "text/markdown")
  public String report(@PathVariable String id) {
    StringBuilder md = new StringBuilder();
    md.append("# Settlement report for ").append(id).append("\n\n");
    List<Map<String, Object>> outcomes = outcomes(id);
    for (Map<String, Object> outcome : outcomes) {
      String outcomeId = String.valueOf(outcome.get("id"));
      md.append("## Outcome ")
          .append(outcomeId)
          .append(" - ")
          .append(outcome.get("name"))
          .append("\n");
      md.append("Milestone value: ")
          .append(outcome.get("milestoneValueMinor"))
          .append(" ")
          .append(outcome.get("currency"))
          .append("\n");
      md.append("Rule: ").append(outcome.get("ruleJson")).append("\n\n");
      List<VerdictRecord> vers = verdictRepository.findByOutcome(outcomeId);
      if (vers.isEmpty()) {
        md.append("_No verdict recorded._\n\n");
        continue;
      }
      VerdictRecord verdict = vers.get(0);
      md.append("Latest verdict: ")
          .append(verdict.status())
          .append(" (")
          .append(verdict.score())
          .append(" bps)\n");
      md.append("Evidence head hash: ").append(verdict.evidenceHeadHash()).append("\n");
      md.append("Rule version: ").append(verdict.ruleVersion()).append("\n");
      md.append("Explanation: ").append(verdict.explanation()).append("\n\n");
    }
    md.append("## Ledger balances\n");
    for (Map<String, Object> b : ledgerBalances()) {
      md.append("- ")
          .append(b.get("accountId"))
          .append(": ")
          .append(b.get("balanceMinor"))
          .append(" ")
          .append(b.get("currency"))
          .append("\n");
    }
    return md.toString();
  }
}
