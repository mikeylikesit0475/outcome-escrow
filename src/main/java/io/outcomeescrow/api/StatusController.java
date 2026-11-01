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
            .sql("select id, currency from ledger_accounts order by id")
            .query(
                (rs, row) -> {
                  java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                  map.put("id", rs.getString("id"));
                  map.put("currency", rs.getString("currency"));
                  return map;
                })
            .list());
    return "status";
  }
}
