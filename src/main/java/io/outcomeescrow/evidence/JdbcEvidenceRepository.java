package io.outcomeescrow.evidence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {

  private final JdbcClient jdbcClient;

  public JdbcEvidenceRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void append(EvidenceRecord record) {
    String id = record.id();
    if (id == null || id.isBlank()) {
      id = java.util.UUID.randomUUID().toString();
    }
    EvidenceRecord normalized =
        new EvidenceRecord(
            id,
            record.contractId(),
            record.outcomeId(),
            record.source(),
            record.sourceRef(),
            record.capturedAt(),
            record.payload(),
            record.hash(),
            record.prevHash(),
            record.synthetic());
    jdbcClient
        .sql(
            """
            insert into evidence (id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic)
            values (:id, :contractId, :outcomeId, :source, :sourceRef, :capturedAt, :payload, :hash, :prevHash, :synthetic)
            """)
        .param("id", normalized.id())
        .param("contractId", normalized.contractId())
        .param("outcomeId", normalized.outcomeId())
        .param("source", normalized.source())
        .param("sourceRef", normalized.sourceRef())
        .param("capturedAt", java.sql.Timestamp.from(normalized.capturedAt()))
        .param("payload", normalized.payload())
        .param("hash", normalized.hash())
        .param("prevHash", normalized.prevHash())
        .param("synthetic", normalized.synthetic())
        .update();
  }

  @Override
  public List<EvidenceRecord> findByOutcome(String outcomeId) {
    return jdbcClient
        .sql(
            """
            select id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic
            from evidence where outcome_id = :outcomeId order by captured_at, hash
            """)
        .param("outcomeId", outcomeId)
        .query(
            (rs, rowNum) ->
                new EvidenceRecord(
                    rs.getString("id"),
                    rs.getString("contract_id"),
                    rs.getString("outcome_id"),
                    rs.getString("source"),
                    rs.getString("source_ref"),
                    rs.getTimestamp("captured_at").toInstant(),
                    rs.getString("payload"),
                    rs.getString("hash"),
                    rs.getString("prev_hash"),
                    rs.getBoolean("synthetic")))
        .list();
  }
}
