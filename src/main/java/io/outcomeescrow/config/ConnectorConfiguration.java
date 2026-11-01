package io.outcomeescrow.config;

import io.outcomeescrow.connectors.github.GithubEvidenceSource;
import io.outcomeescrow.connectors.jira.JiraFixtureEvidenceSource;
import io.outcomeescrow.connectors.salesforce.SalesforceFixtureEvidenceSource;
import io.outcomeescrow.connectors.snowflake.SnowflakeFixtureEvidenceSource;
import io.outcomeescrow.evidence.EvidenceSource;
import io.outcomeescrow.evidence.EvidenceSourceRegistry;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ConnectorConfiguration {
  @Bean
  public JiraFixtureEvidenceSource jiraFixtureEvidenceSource() {
    return new JiraFixtureEvidenceSource();
  }

  @Bean
  public SalesforceFixtureEvidenceSource salesforceFixtureEvidenceSource() {
    return new SalesforceFixtureEvidenceSource();
  }

  @Bean
  public SnowflakeFixtureEvidenceSource snowflakeFixtureEvidenceSource() {
    return new SnowflakeFixtureEvidenceSource();
  }

  @Bean
  public GithubEvidenceSource githubEvidenceSource() {
    return new GithubEvidenceSource(
        RestClient.builder(),
        "mikeylikesit0475",
        "outcome-escrow",
        System.getenv().getOrDefault("GITHUB_TOKEN", ""));
  }

  @Bean
  public EvidenceSourceRegistry evidenceSourceRegistry(List<EvidenceSource> sources) {
    EvidenceSourceRegistry registry = new EvidenceSourceRegistry();
    sources.forEach(registry::register);
    return registry;
  }
}
