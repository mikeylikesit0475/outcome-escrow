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
    jdbcClient
        .sql(
            """
            insert into evidence (id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash)
            values (:id, :contractId, :outcomeId, :source, :sourceRef, :capturedAt, :payload, :hash, :prevHash)
            """)
        .param("id", record.id())
        .param("contractId", record.contractId())
        .param("outcomeId", record.outcomeId())
        .param("source", record.source())
        .param("sourceRef", record.sourceRef())
        .param("capturedAt", java.sql.Timestamp.from(record.capturedAt()))
        .param("payload", record.payload())
        .param("hash", record.hash())
        .param("prevHash", record.prevHash())
        .update();
  }

  @Override
  public List<EvidenceRecord> findByOutcome(String outcomeId) {
    return jdbcClient
        .sql(
            """
            select id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash
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
                    rs.getString("prev_hash")))
        .list();
  }
}
