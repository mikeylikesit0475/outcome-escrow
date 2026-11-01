package io.outcomeescrow;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class ReplayAndIdempotencyTests {

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
  void evaluateTwiceWithTwoOutcomesPostsOnce() throws Exception {
    jdbcClient
        .sql("insert into contracts (id, name, currency) values ('c-two', 'two', 'USD')")
        .update();
    jdbcClient
        .sql(
            """
            insert into outcomes (id, contract_id, name, rule, rule_json, tiers_json, milestone_minor, currency, connector, metric, window_days, params)
            values ('o-two-a', 'c-two', 'A', '{}', :rule, :tiers, 1000, 'USD', null, null, 30, null),
                   ('o-two-b', 'c-two', 'B', '{}', :rule, :tiers, 1000, 'USD', null, null, 30, null)
            """)
        .param(
            "rule",
            "{\"type\":\"Threshold\",\"metric\":\"metric\",\"operator\":\"AT_LEAST\",\"target\":1.0}")
        .param(
            "tiers",
            "{\"floorScore\":0,\"clawbackBasisPointsBelowFloor\":0,\"tiers\":[{\"minimumScore\":0,\"payoutBasisPoints\":10000}]}")
        .update();
    for (String outcomeId : new String[] {"o-two-a", "o-two-b"}) {
      evidenceRepository.append(
          new EvidenceRecord(
              null,
              "c-two",
              outcomeId,
              "fixture",
              "ref",
              Instant.parse("2026-01-01T00:00:00Z"),
              "{\"value\":1}",
              null,
              null,
              false));
    }

    mockMvc.perform(post("/contracts/c-two/evaluate")).andExpect(status().isOk());
    mockMvc.perform(post("/contracts/c-two/evaluate")).andExpect(status().isOk());

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
    org.junit.jupiter.api.Assertions.assertEquals(-2000L, escrow);
  }

  @Test
  void replayStaysIdenticalAfterNewEvidence() throws Exception {
    jdbcClient
        .sql("insert into contracts (id, name, currency) values ('c-replay', 'replay', 'USD')")
        .update();
    jdbcClient
        .sql(
            """
            insert into outcomes (id, contract_id, name, rule, rule_json, tiers_json, milestone_minor, currency, connector, metric, window_days, params)
            values ('o-replay', 'c-replay', 'R', '{}', :rule, :tiers, 1000, 'USD', null, null, 30, null)
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
            "c-replay",
            "o-replay",
            "fixture",
            "ref",
            Instant.parse("2026-01-01T00:00:00Z"),
            "{\"value\":1}",
            null,
            null,
            false));

    String body =
        mockMvc
            .perform(post("/contracts/c-replay/evaluate"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String verdictId =
        body.substring(
            body.indexOf("\"verdictId\":\"") + 13,
            body.indexOf("\"", body.indexOf("\"verdictId\":\"") + 13));

    // append newer evidence; replay must still match the original verdict
    evidenceRepository.append(
        new EvidenceRecord(
            null,
            "c-replay",
            "o-replay",
            "fixture",
            "ref2",
            Instant.parse("2026-02-01T00:00:00Z"),
            "{\"value\":1}",
            null,
            null,
            false));

    String replay =
        mockMvc
            .perform(post("/verdicts/" + verdictId + "/replay"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertTrue(replay.contains("\"identical\":true"));
  }
}
