package io.outcomeescrow.connectors.fixture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.outcomeescrow.domain.Evidence;
import io.outcomeescrow.domain.Outcome;
import io.outcomeescrow.domain.Window;
import io.outcomeescrow.evidence.EvidenceSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.core.io.ClassPathResource;

public abstract class FixtureEvidenceSource implements EvidenceSource {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final String connectorId;
  private final JsonNode root;

  protected FixtureEvidenceSource(String connectorId, String resourcePath) {
    this.connectorId = connectorId;
    try (InputStream stream = new ClassPathResource(resourcePath).getInputStream()) {
      this.root = MAPPER.readTree(stream);
    } catch (Exception e) {
      throw new IllegalStateException("unable to load fixture " + resourcePath, e);
    }
    if (!root.path("synthetic").asBoolean(false)) {
      throw new IllegalStateException("fixture must carry synthetic: true");
    }
  }

  protected JsonNode root() {
    return root;
  }

  @Override
  public String id() {
    return connectorId;
  }

  @Override
  public boolean synthetic() {
    return true;
  }

  @Override
  public List<Evidence> fetch(Outcome outcome, Window window, Instant now) {
    if (outcome == null || !connectorId.equals(outcome.connector())) {
      return List.of();
    }
    JsonNode payload = root.path("metrics").path(outcome.metric());
    if (payload.isMissingNode() || payload.isNull()) {
      return List.of();
    }
    String json = payload.toString();
    String hash = sha256(json + outcome.id() + now);
    String sourceRef = connectorId + ":" + outcome.metric();
    return List.of(
        new Evidence(null, null, null, connectorId, sourceRef, now, json, hash, null, true));
  }

  protected static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
