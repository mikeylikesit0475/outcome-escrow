package io.outcomeescrow.services;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WebhookService {
  private final String webhookUrl;
  private final String secret;
  private final RestClient client = RestClient.create();

  public WebhookService(
      @Value("${SETTLEMENT_WEBHOOK_URL:}") String webhookUrl,
      @Value("${SETTLEMENT_WEBHOOK_SECRET:}") String secret) {
    this.webhookUrl = webhookUrl == null ? "" : webhookUrl;
    this.secret = secret == null ? "" : secret;
  }

  public boolean send(Map<String, Object> payload) {
    if (webhookUrl.isBlank()) {
      return false;
    }
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      String body = mapper.writeValueAsString(payload);
      String signature = sign(body);
      client
          .post()
          .uri(webhookUrl)
          .header("Content-Type", "application/json")
          .header("X-Signature-256", "sha256=" + signature)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  private String sign(String body) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
