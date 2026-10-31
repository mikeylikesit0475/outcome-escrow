package io.outcomeescrow.connectors.salesforce;

import io.outcomeescrow.connectors.fixture.FixtureEvidenceSource;

public class SalesforceFixtureEvidenceSource extends FixtureEvidenceSource {
  public SalesforceFixtureEvidenceSource() {
    super("salesforce-fixture", "fixtures/salesforce/fixture.json");
  }
}
