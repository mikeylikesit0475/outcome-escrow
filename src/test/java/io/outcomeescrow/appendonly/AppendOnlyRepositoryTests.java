package io.outcomeescrow.appendonly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.outcomeescrow.evidence.EvidenceRecord;
import io.outcomeescrow.evidence.JdbcEvidenceRepository;
import io.outcomeescrow.ledger.persistence.JdbcLedgerPostingRepository;
import io.outcomeescrow.ledger.persistence.LedgerPostingRecord;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class AppendOnlyRepositoryTests {

  @Container static PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", database::getJdbcUrl);
    registry.add("spring.datasource.username", database::getUsername);
    registry.add("spring.datasource.password", database::getPassword);
  }

  @Autowired JdbcClient jdbcClient;
  @Autowired JdbcEvidenceRepository evidenceRepository;
  @Autowired JdbcLedgerPostingRepository postingRepository;

  @BeforeEach
  void seed() {
    jdbcClient
        .sql(
            """
            insert into contracts (id, name, currency)
            values ('c1', 'contract', 'USD')
            on conflict (id) do nothing
            """)
        .update();
    jdbcClient
        .sql(
            """
            insert into outcomes (id, contract_id, name, rule, milestone_minor, currency)
            values ('o1', 'c1', 'outcome', 'rule', 1000, 'USD')
            on conflict (id) do nothing
            """)
        .update();
  }

  @Test
  void evidenceTriggerRejectsUpdate() {
    EvidenceRecord record =
        new EvidenceRecord(
            "e1",
            "c1",
            "o1",
            "fixture",
            "ref-1",
            Instant.parse("2026-01-01T00:00:00Z"),
            "{\"value\":1}",
            "hash-e1",
            null,
            false);
    evidenceRepository.append(record);

    DataAccessException thrown =
        assertThrows(
            DataAccessException.class,
            () ->
                jdbcClient
                    .sql("update evidence set payload = :payload where id = :id")
                    .param("payload", "{\"value\":2}")
                    .param("id", "e1")
                    .update());
    assertTrue(
        thrown.getRootCause() instanceof SQLException || thrown.getCause() instanceof SQLException);
  }

  @Test
  void evidenceTriggerRejectsDelete() {
    EvidenceRecord record =
        new EvidenceRecord(
            "e2",
            "c1",
            "o1",
            "fixture",
            "ref-2",
            Instant.parse("2026-01-01T00:00:00Z"),
            "{\"value\":1}",
            "hash-e2",
            null,
            false);
    evidenceRepository.append(record);
    assertThrows(
        DataAccessException.class,
        () -> jdbcClient.sql("delete from evidence where id = :id").param("id", "e2").update());
  }

  @Test
  void postingTriggerRejectsUpdate() {
    postingRepository.ensureAccount("escrow", "USD");
    postingRepository.ensureAccount("vendor", "USD");
    LedgerPostingRecord record =
        new LedgerPostingRecord(
            "p-update",
            "ik-posting-update",
            "escrow",
            "vendor",
            1000L,
            "USD",
            Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(postingRepository.append(record));

    assertThrows(
        DataAccessException.class,
        () ->
            jdbcClient
                .sql("update ledger_postings set amount_minor = :amount where id = :id")
                .param("amount", 2000L)
                .param("id", "p-update")
                .update());
  }

  @Test
  void postingTriggerRejectsDelete() {
    postingRepository.ensureAccount("escrow", "USD");
    postingRepository.ensureAccount("vendor", "USD");
    LedgerPostingRecord record =
        new LedgerPostingRecord(
            "p-delete",
            "ik-posting-delete",
            "escrow",
            "vendor",
            1000L,
            "USD",
            Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(postingRepository.append(record));

    assertThrows(
        DataAccessException.class,
        () ->
            jdbcClient
                .sql("delete from ledger_postings where id = :id")
                .param("id", "p-delete")
                .update());
  }

  @Test
  void postingIdempotencyKeyIsUnique() {
    LedgerPostingRecord first =
        new LedgerPostingRecord(
            "p1", "ik-1", "escrow", "vendor", 1000L, "USD", Instant.parse("2026-01-01T00:00:00Z"));
    assertTrue(postingRepository.append(first));

    postingRepository.ensureAccount("escrow", "USD");
    postingRepository.ensureAccount("vendor", "USD");
    LedgerPostingRecord duplicate =
        new LedgerPostingRecord(
            "p2", "ik-1", "escrow", "vendor", 1000L, "USD", Instant.parse("2026-01-01T00:00:00Z"));
    assertEquals(false, postingRepository.append(duplicate));
  }
}
