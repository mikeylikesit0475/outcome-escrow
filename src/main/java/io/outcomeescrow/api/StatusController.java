package io.outcomeescrow.api;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatusController {
  private final JdbcClient jdbcClient;

  public StatusController(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @GetMapping("/status")
  public String status(Model model) {
    model.addAttribute(
        "contracts",
        jdbcClient
            .sql("select id, name, currency from contracts order by id")
            .query(
                (rs, row) -> {
                  java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("name", rs.getString("name"));
                  map.put("currency", rs.getString("currency"));
                  return map;
                })
            .list());
    model.addAttribute(
        "outcomes",
        jdbcClient
            .sql("select id, contract_id, name, metric from outcomes order by contract_id, id")
            .query(
                (rs, row) -> {
                  java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("contractId", rs.getString("contract_id"));
                  map.put("name", rs.getString("name"));
                  map.put("metric", rs.getString("metric"));
                  return map;
                })
            .list());
    model.addAttribute(
        "verdicts",
        jdbcClient
            .sql(
                "select id, outcome_id, status, score, evaluated_at from verdicts order by evaluated_at desc")
            .query(
                (rs, row) -> {
                  java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("outcomeId", rs.getString("outcome_id"));
                  map.put("status", rs.getString("status"));
                  map.put("score", rs.getInt("score"));
                  map.put("evaluatedAt", rs.getTimestamp("evaluated_at").toInstant().toString());
                  return map;
                })
            .list());
    model.addAttribute(
        "accounts",
        jdbcClient
            .sql(
                """
                select a.id, a.currency,
                  coalesce(sum(case when p.to_account_id = a.id then p.amount_minor else 0 end), 0)
                  - coalesce(sum(case when p.from_account_id = a.id then p.amount_minor else 0 end), 0)
                  as balance_minor
                from ledger_accounts a
                left join ledger_postings p on p.from_account_id = a.id or p.to_account_id = a.id
                group by a.id, a.currency
                order by a.id
                """)
            .query(
                (rs, row) -> {
                  java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("currency", rs.getString("currency"));
                  map.put("balanceMinor", rs.getLong("balance_minor"));
                  return map;
                })
            .list());
    return "status";
  }
}
