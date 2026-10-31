package io.outcomeescrow.connectors.jira;

import io.outcomeescrow.connectors.fixture.FixtureEvidenceSource;

public class JiraFixtureEvidenceSource extends FixtureEvidenceSource {
  public JiraFixtureEvidenceSource() {
    super("jira-fixture", "fixtures/jira/fixture.json");
  }
}
