package io.outcomeescrow.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.Window;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubEvidenceSourceTests {
  @Test
  void ciRunsCountsRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String nowText = "2026-10-31T00:00:00Z";
    String body =
        "{\"workflow_runs\":["
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"success\",\"created_at\":\""
            + nowText
            + "\"},"
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"failure\",\"created_at\":\""
            + nowText
            + "\"}]}";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "ci-total", "CI total", null, 0L, "USD", "github", "ci_runs_total", Map.of(), null);
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"value\":2}", result.get(0).payload());
  }

  @Test
  void ciRunsPassedAndWindowFilterRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String nowText = "2026-10-31T00:00:00Z";
    String oldText = "2026-09-01T00:00:00Z";
    String body =
        "{\"workflow_runs\":["
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"success\",\"created_at\":\""
            + nowText
            + "\"},"
            + "{\"status\":\"completed\",\"head_branch\":\"main\",\"conclusion\":\"success\",\"created_at\":\""
            + oldText
            + "\"}]}";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "ci-passed", "CI passed", null, 0L, "USD", "github", "ci_runs_passed", Map.of(), null);
    Instant now = Instant.parse(nowText);
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"value\":1}", result.get(0).payload());
  }

  @Test
  void prMedianHoursRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String body =
        "["
            + "{\"created_at\":\"2026-10-28T00:00:00Z\",\"merged_at\":\"2026-10-28T03:00:00Z\"},"
            + "{\"created_at\":\"2026-10-29T00:00:00Z\",\"merged_at\":\"2026-10-29T05:00:00Z\"}]";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "pr",
            "PR median",
            null,
            0L,
            "USD",
            "github",
            "pr_median_hours_to_merge",
            Map.of(),
            null);
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"value\":4.0}", result.get(0).payload());
  }

  @Test
  void milestoneClosedAtRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String body = "[{\"title\":\"Sprint 2\",\"number\":2,\"closed_at\":\"2026-10-30T12:00:00Z\"}]";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "ms",
            "Sprint 2",
            null,
            0L,
            "USD",
            "github",
            "milestone_closed_at",
            Map.of("milestone", "Sprint 2"),
            null);
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"eventAt\":\"2026-10-30T12:00:00Z\"}", result.get(0).payload());
  }

  @Test
  void openBugsOlderThanDaysRecordedResponse() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    GithubEvidenceSource source = new GithubEvidenceSource(builder, "test-token");

    String body =
        "["
            + "{\"created_at\":\"2026-10-01T00:00:00Z\"},"
            + "{\"created_at\":\"2026-10-30T00:00:00Z\",\"pull_request\":{\"url\":\"x\"}}]";
    server
        .expect(requestTo(org.hamcrest.Matchers.startsWith(GithubEvidenceSource.BASE_URL)))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    Outcome outcome =
        new Outcome(
            "bugs",
            "Open bugs",
            null,
            0L,
            "USD",
            "github",
            "open_bugs_older_than_days",
            Map.of("days", "3"),
            null);
    Instant now = Instant.parse("2026-10-31T00:00:00Z");
    var result = source.fetch(outcome, Window.trailingDays(now, 30), now);
    assertEquals("{\"value\":1}", result.get(0).payload());
  }
}
