package io.outcomeescrow.seeds;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.outcomeescrow.domain.RuleSpec;
import io.outcomeescrow.domain.TierTable;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class SeedLoader implements ApplicationRunner {
  private final JdbcClient jdbcClient;

  public SeedLoader(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  public record ContractYaml(String id, String name, String currency, List<OutcomeYaml> outcomes) {}

  public record OutcomeYaml(
      String id,
      String name,
      String connector,
      String metric,
      Integer windowDays,
      Map<String, String> params,
      long value,
      RuleYaml rule,
      TierTableYaml tiers) {}

  public record RuleYaml(
      String type,
      String metric,
      String operator,
      double target,
      String numeratorMetric,
      String denominatorMetric,
      String eventMetric,
      String dueAt) {}

  public record TierTableYaml(
      int floorScore, int clawbackBasisPointsBelowFloor, List<TierYaml> tiers) {}

  public record TierYaml(int minimumScore, int payoutBasisPoints) {}

  @Override
  public void run(ApplicationArguments args) {
    Long count = jdbcClient.sql("select count(*) from contracts").query(Long.class).single();
    if (count != null && count > 0) {
      return;
    }
    ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources;
    try {
      resources = resolver.getResources("classpath:contracts/*.yaml");
      Arrays.sort(resources, (a, b) -> a.getFilename().compareTo(b.getFilename()));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    for (Resource resource : resources) {
      try {
        ContractYaml contract = yaml.readValue(resource.getInputStream(), ContractYaml.class);
        seedContract(contract);
      } catch (Exception e) {
        throw new IllegalStateException("could not seed " + resource.getFilename(), e);
      }
    }
  }

  private void seedContract(ContractYaml contract) {
    jdbcClient
        .sql("insert into contracts (id, name, currency) values (:id, :name, :currency)")
        .param("id", contract.id())
        .param("name", contract.name())
        .param("currency", contract.currency())
        .update();

    for (OutcomeYaml outcome : contract.outcomes()) {
      insertOutcome(contract.id(), contract.currency(), outcome);
    }
  }

  private void insertOutcome(String contractId, String currency, OutcomeYaml source) {
    RuleSpec ruleSpec = toRule(source.rule());
    TierTable tierTable = toTiers(source.tiers());
    String rawParams = source.params() == null ? null : SeedLoader.paramsJson(source.params());
    jdbcClient
        .sql(
            """
            insert into outcomes (
              id, contract_id, name, rule, rule_json, tiers_json, milestone_minor, currency,
              connector, metric, window_days, params
            ) values (
              :id, :contractId, :name, :rule, :ruleJson, :tiersJson, :value, :currency,
              :connector, :metric, :windowDays, :params
            )
            """)
        .param("id", source.id())
        .param("contractId", contractId)
        .param("name", source.name())
        .param("rule", OutcomeJson.ruleJson(ruleSpec))
        .param("ruleJson", OutcomeJson.ruleJson(ruleSpec))
        .param("tiersJson", OutcomeJson.tierJson(tierTable))
        .param("value", source.value())
        .param("currency", currency)
        .param("connector", source.connector())
        .param("metric", source.metric())
        .param("windowDays", source.windowDays() == null ? 30 : source.windowDays())
        .param("params", rawParams)
        .update();
  }

  private RuleSpec toRule(RuleYaml yaml) {
    if (yaml == null || yaml.type() == null || yaml.type().isBlank()) {
      return null;
    }
    return switch (yaml.type()) {
      case "Threshold" ->
          new RuleSpec.Threshold(
              yaml.metric() == null || yaml.metric().isBlank() ? "" : yaml.metric(),
              RuleSpec.Operator.valueOf(yaml.operator()),
              yaml.target());
      case "WindowedRate" ->
          new RuleSpec.WindowedRate(
              yaml.numeratorMetric(),
              yaml.denominatorMetric(),
              RuleSpec.Operator.valueOf(yaml.operator()),
              yaml.target());
      case "Deadline" ->
          new RuleSpec.Deadline(yaml.eventMetric(), java.time.Instant.parse(yaml.dueAt()));
      default -> throw new IllegalArgumentException("unknown rule: " + yaml.type());
    };
  }

  private TierTable toTiers(TierTableYaml yaml) {
    if (yaml == null) {
      return null;
    }
    List<TierTable.Tier> tiers =
        yaml.tiers().stream()
            .map(item -> new TierTable.Tier(item.minimumScore(), item.payoutBasisPoints()))
            .toList();
    return new TierTable(yaml.floorScore(), yaml.clawbackBasisPointsBelowFloor(), tiers);
  }

  static String paramsJson(Map<String, String> params) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(params);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
