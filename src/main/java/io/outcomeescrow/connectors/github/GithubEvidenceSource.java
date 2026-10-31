package io.outcomeescrow.connectors.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.outcomeescrow.domain.Evidence;
import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.Window;
import io.outcomeescrow.evidence.EvidenceSource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.web.client.RestClient;

public class GithubEvidenceSource implements EvidenceSource {
  public static final String BASE_URL = "https://api.github.com";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final RestClient client;
  private final String owner;
  private final String repo;
  private final String token;

  public GithubEvidenceSource(RestClient.Builder builder, String owner, String repo, String token) {
    this.owner = owner;
    this.repo = repo;
    this.token = token == null ? "" : token;
    this.client = builder.baseUrl(BASE_URL).build();
  }

  public GithubEvidenceSource(RestClient.Builder builder, String token) {
    this(builder, "mikeylikesit0475", "outcome-escrow", token);
  }

  @Override
  public String id() {
    return "github";
  }

  @Override
  public boolean synthetic() {
    return false;
  }

  @Override
  public List<Evidence> fetch(Outcome outcome, Window window, Instant now) {
    if (outcome == null) {
      return List.of();
    }
    String metric = outcome.metric();
    if (metric == null) {
      return List.of();
    }
    String valueJson = switch (metric) {
      case "ci_runs_passed" -> ciRuns(now, true);
      case "ci_runs_total" -> ciRuns(now, false);
      case "pr_median_hours_to_merge" -> prMedian(now, window);
      case "milestone_closed_at" -> milestone(outcome);
      case "open_bugs_older_than_days" -> openBugs(now, outcome, window);
      default -> null;
    };
    if (valueJson == null) {
      return List.of();
    }
    String hash = sha256(valueJson + outcome.id() + now);
    return List.of(new Evidence(null, null, null, id(), metric, now, valueJson, hash, null, false));
  }

  private String repoPath() {
    return "/repos/" + owner + "/" + repo;
  }

  private JsonNode get(String pathAndQuery) {
    String raw =
        client
            .get()
            .uri(pathAndQuery)
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(String.class);
    try {
      return MAPPER.readTree(raw);
    } catch (Exception e) {
      return MAPPER.createArrayNode();
    }
  }

  private String ciRuns(Instant now, boolean passedOnly) {
    try {
      JsonNode node = get(repoPath() + "/actions/runs?branch=main&per_page=100");
      int total = 0;
      int passed = 0;
      if (node != null && node.path("workflow_runs").isArray()) {
        for (JsonNode run : node.path("workflow_runs")) {
          if (!"completed".equals(run.path("status").asText())) {
            continue;
          }
          if (!"main".equals(run.path("head_branch").asText("main"))) {
            continue;
          }
          total++;
          if ("success".equals(run.path("conclusion").asText())) {
            passed++;
          }
        }
      }
      int value = passedOnly ? passed : total;
      return "{\"value\":" + value + "}";
    } catch (Exception e) {
      return null;
    }
  }

  private String prMedian(Instant now, Window window) {
    try {
      JsonNode node = get(repoPath() + "/pulls?state=closed&per_page=100");
      List<Long> durations = new ArrayList<>();
      if (node != null && node.isArray()) {
        for (JsonNode pr : node) {
          if (pr.path("merged_at").isNull() || pr.path("merged_at").isMissingNode()) {
            continue;
          }
          Instant merged = Instant.parse(pr.path("merged_at").asText());
          Instant created = Instant.parse(pr.path("created_at").asText());
          if (merged.isBefore(window.from()) || merged.isAfter(window.to())) {
            continue;
          }
          durations.add(java.time.Duration.between(created, merged).toHours());
        }
      }
      if (durations.isEmpty()) {
        return "{\"value\":0}";
      }
      durations.sort(Comparator.naturalOrder());
      long median;
      if (durations.size() % 2 == 0) {
        median = (durations.get(durations.size() / 2 - 1) + durations.get(durations.size() / 2)) / 2;
      } else {
        median = durations.get(durations.size() / 2);
      }
      return "{\"value\":" + median + "}";
    } catch (Exception e) {
      return null;
    }
  }

  private String milestone(Outcome outcome) {
    String wanted = null;
    Map<String, String> params = outcome.params();
    if (params != null) {
      wanted = params.get("milestone");
    }
    if (wanted == null || wanted.isBlank()) {
      wanted = outcome.name();
    }
    try {
      JsonNode node = get(repoPath() + "/milestones?state=all&per_page=100");
      if (node != null && node.isArray()) {
        for (JsonNode item : node) {
          String title = item.path("title").asText("");
          String number = String.valueOf(item.path("number").asInt(-1));
          if (wanted.equals(title) || wanted.equals(number)) {
            if (item.path("closed_at").isNull() || item.path("closed_at").isMissingNode()) {
              return "{\"eventAt\":null}";
            }
            return "{\"eventAt\":\"" + item.path("closed_at").asText() + "\"}";
          }
        }
      }
      return "{\"eventAt\":null}";
    } catch (Exception e) {
      return null;
    }
  }

  private String openBugs(Instant now, Outcome outcome, Window window) {
    int days = 7;
    Map<String, String> params = outcome.params();
    if (params != null && params.get("days") != null) {
      try {
        days = Integer.parseInt(params.get("days"));
      } catch (NumberFormatException ignored) {
      }
    }
    Instant cutoff = now.minusSeconds((long) days * 86400L);
    try {
      JsonNode node = get(repoPath() + "/issues?state=open&labels=bug&per_page=100");
      int count = 0;
      if (node != null && node.isArray()) {
        for (JsonNode issue : node) {
          if (issue.has("pull_request")) {
            continue;
          }
          String createdText = issue.path("created_at").asText();
          if (createdText.isBlank()) {
            continue;
          }
          Instant created = Instant.parse(createdText);
          if (created.isBefore(cutoff)) {
            count++;
          }
        }
      }
      return "{\"value\":" + count + "}";
    } catch (Exception e) {
      return null;
    }
  }

  private static String sha256(String value) {
    try {
      java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
      return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
