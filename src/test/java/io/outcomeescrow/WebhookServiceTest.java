package io.outcomeescrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.outcomeescrow.services.WebhookService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {
  @Test
  void sendAddsValidHmacSignature() throws Exception {
    String secret = "top-secret";
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("type", "SETTLEMENT_POSTED");
    payload.put("amountMinor", 1000L);

    StringBuilder capturedBody = new StringBuilder();
    StringBuilder capturedSignature = new StringBuilder();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/hook",
        exchange -> {
          byte[] bytes = exchange.getRequestBody().readAllBytes();
          capturedBody.append(new String(bytes, StandardCharsets.UTF_8));
          capturedSignature.append(exchange.getRequestHeaders().getFirst("X-Signature-256"));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();
    try {
      int port = server.getAddress().getPort();
      WebhookService service = new WebhookService("http://localhost:" + port + "/hook", secret);
      assertTrue(service.send(payload));

      String expected = "sha256=" + hmacBase64(secret, capturedBody.toString());
      assertEquals(expected, capturedSignature.toString());
    } finally {
      server.stop(0);
    }
  }

  private static String hmacBase64(String secret, String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }
}
