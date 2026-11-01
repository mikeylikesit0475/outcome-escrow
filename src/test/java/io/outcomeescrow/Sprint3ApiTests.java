package io.outcomeescrow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.outcomeescrow.evidence.EvidenceRecord;
import io.outcomeescrow.evidence.EvidenceRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class Sprint3ApiTests {

  @Container static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", database::getJdbcUrl);
    registry.add("spring.datasource.username", database::getUsername);
    registry.add("spring.datasource.password", database::getPassword);
  }

  @Autowired MockMvc mockMvc;
  @Autowired JdbcClient jdbcClient;
  @Autowired EvidenceRepository evidenceRepository;

  @Test
  void evaluateIsIdempotentAndReplayMatches() throws Exception {
    jdbcClient
        .sql("insert into contracts (id, name, currency) values ('c-api', 'api', 'USD')")
        .update();
    jdbcClient
        .sql(
            """
            insert into outcomes (id, contract_id, name, rule, rule_json, tiers_json, milestone_minor, currency, connector, metric, window_days, params)
            values ('o-api', 'c-api', 'API', '{}', :rule, :tiers, 1000, 'USD', 'fixture', 'metric', 30, null)
            """)
        .param(
            "rule",
            "{\"type\":\"Threshold\",\"metric\":\"metric\",\"operator\":\"AT_LEAST\",\"target\":1.0}")
        .param(
            "tiers",
            "{\"floorScore\":0,\"clawbackBasisPointsBelowFloor\":0,\"tiers\":[{\"minimumScore\":0,\"payoutBasisPoints\":10000}]}")
        .update();
    evidenceRepository.append(
        new EvidenceRecord(
            null,
            "c-api",
            "o-api",
            "fixture",
            "ref",
            Instant.parse("2026-01-01T00:00:00Z"),
            "{\"value\":1}",
            null,
            null,
            false));

    String body =
        mockMvc
            .perform(post("/contracts/c-api/evaluate"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(body.contains("\"status\":\"MET\""));
    assertTrue(body.contains("\"posted\":true"));

    String verdictId =
        body.substring(
            body.indexOf("\"verdictId\":\"") + 13,
            body.indexOf("\"", body.indexOf("\"verdictId\":\"") + 13));
    String replay =
        mockMvc
            .perform(post("/verdicts/" + verdictId + "/replay"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(replay.contains("\"identical\":true"));

    // second evaluate posts nothing
    @SuppressWarnings("unused")
    String second =
        mockMvc
            .perform(post("/contracts/c-api/evaluate"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long escrow =
        jdbcClient
            .sql(
                """
                select coalesce(sum(case when to_account_id='ESCROW' then amount_minor else 0 end),0)
                     - coalesce(sum(case when from_account_id='ESCROW' then amount_minor else 0 end),0)
                from ledger_postings
                """)
            .query(Long.class)
            .single();
    assertEquals(-1000, escrow);

    mockMvc.perform(get("/status")).andExpect(status().isOk());
    mockMvc
        .perform(get("/contracts/c-api/report"))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Settlement report")));
    mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }
}
