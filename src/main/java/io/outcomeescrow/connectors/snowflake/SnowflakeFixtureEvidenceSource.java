package io.outcomeescrow.connectors.snowflake;

import io.outcomeescrow.connectors.fixture.FixtureEvidenceSource;

public class SnowflakeFixtureEvidenceSource extends FixtureEvidenceSource {
  public SnowflakeFixtureEvidenceSource() {
    super("snowflake-fixture", "fixtures/snowflake/fixture.json");
  }
}
