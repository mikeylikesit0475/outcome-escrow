package io.outcomeescrow.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.Window;
import io.outcomeescrow.domain.RuleSpec;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class GithubEvidenceSourceTests {
  @Test
  void ciRunsCountsRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String body =
        "{\"workflow_runs\":["
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"success\"},"
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"failure\"}]}";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "ci-total",
            "CI total",
            null,
            0L,
            "USD",
            "github",
            "ci_runs_total",
            Map.of(),
            null);
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"value\":2}", result.get(0).payload());
  }
}
