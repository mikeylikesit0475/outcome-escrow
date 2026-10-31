package io.outcomeescrow.seeds;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class SeedLoader implements ApplicationRunner {
  private final JdbcClient jdbcClient;

  public SeedLoader(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void run(ApplicationArguments args) {
    Long count = jdbcClient.sql("select count(*) from contracts").query(Long.class).single();
    if (count != null && count > 0) {
      return;
    }
    seedContract("self-delivery", "Outcome Escrow self-delivery", "USD");
    outcome(
        "self-delivery",
        "self-sprint-1-milestones",
        "Sprint milestones closed by due date",
        "github",
        "milestone_closed_at",
        "{\"milestone\":\"Sprint 1\"}",
        30);
    outcome("self-delivery", "self-ci-runs-passed", "CI runs passed on main", "github", "ci_runs_passed", null, 30);
    outcome("self-delivery", "self-ci-runs-total", "CI runs total on main", "github", "ci_runs_total", null, 30);
    outcome("self-delivery", "self-pr-median-hours", "PR median hours to merge", "github", "pr_median_hours_to_merge", null, 30);
    outcome(
        "self-delivery",
        "self-open-bugs-older-than-seven-days",
        "Open bug issues older than seven days",
        "github",
        "open_bugs_older_than_days",
        "{\"days\":\"7\"}",
        30);

    seedContract("acme-vendor", "Acme vendor demo contract", "USD");
    outcome("acme-vendor", "acme-jira-p1-sla", "Jira P1 resolved within SLA", "jira-fixture", "p1_resolved_within_sla_rate", null, 30);
    outcome("acme-vendor", "acme-salesforce-csat", "Salesforce case CSAT average", "salesforce-fixture", "case_csat_avg", null, 30);
    outcome("acme-vendor", "acme-snowflake-load", "Snowflake nightly load completed", "snowflake-fixture", "nightly_load_completed_at", null, 30);
  }

  private void seedContract(String id, String name, String currency) {
    jdbcClient
        .sql("insert into contracts (id, name, currency) values (:id, :name, :currency)")
        .param("id", id)
        .param("name", name)
        .param("currency", currency)
        .update();
  }

  private void outcome(
      String contractId,
      String id,
      String name,
      String connector,
      String metric,
      String params,
      int windowDays) {
    jdbcClient
        .sql(
            """
            insert into outcomes (id, contract_id, name, rule, milestone_minor, currency, connector, metric, window_days, params)
            values (:id, :contractId, :name, :rule, :milestoneMinor, :currency, :connector, :metric, :windowDays, :params)
            """)
        .param("id", id)
        .param("contractId", contractId)
        .param("name", name)
        .param("rule", "UNSET")
        .param("milestoneMinor", 1000L)
        .param("currency", "USD")
        .param("connector", connector)
        .param("metric", metric)
        .param("windowDays", windowDays)
        .param("params", params)
        .update();
  }
}
