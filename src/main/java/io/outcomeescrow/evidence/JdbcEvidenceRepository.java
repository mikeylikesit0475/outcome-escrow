package io.outcomeescrow.evidence;

import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcEvidenceRepository implements EvidenceRepository {

  private static final java.security.MessageDigest MD;

  static {
    try {
      MD = java.security.MessageDigest.getInstance("SHA-256");
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private final JdbcClient jdbcClient;

  public JdbcEvidenceRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Transactional
  @Override
  public void append(EvidenceRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("record is required");
    }
    jdbcClient
        .sql("select 1 from contracts where id = :contractId for update")
        .param("contractId", record.contractId())
        .query(Integer.class)
        .optional();

    long seq =
        jdbcClient
            .sql(
                """
                select coalesce(max(seq), 0) + 1
                from evidence
                where contract_id = :contractId
                """)
            .param("contractId", record.contractId())
            .query(Long.class)
            .single();

    String computedHash = sha256(record.payload() == null ? "" : record.payload());
    EvidenceRecord normalized =
        new EvidenceRecord(
            record.id() == null || record.id().isBlank()
                ? java.util.UUID.randomUUID().toString()
                : record.id(),
            record.contractId(),
            record.outcomeId(),
            record.source(),
            record.sourceRef(),
            record.capturedAt(),
            record.payload(),
            computedHash,
            hashOfPrevious(record.contractId()),
            record.synthetic());

    jdbcClient
        .sql(
            """
            insert into evidence (
              id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic, seq
            ) values (
              :id, :contractId, :outcomeId, :source, :sourceRef, :capturedAt, :payload, :hash, :prevHash, :synthetic, :seq
            )
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
        .param("seq", seq)
        .update();
  }

  private String hashOfPrevious(String contractId) {
    return jdbcClient
        .sql(
            """
            select hash
            from evidence
            where contract_id = :contractId
            order by seq desc
            limit 1
            """)
        .param("contractId", contractId)
        .query(String.class)
        .optional()
        .orElse(null);
  }

  @Override
  public List<EvidenceRecord> findByOutcome(String outcomeId) {
    return query(" where outcome_id = :outcomeId order by captured_at, id", "outcomeId", outcomeId);
  }

  @Override
  public List<EvidenceRecord> findByContract(String contractId) {
    return query(" where contract_id = :contractId order by seq", "contractId", contractId);
  }

  private List<EvidenceRecord> query(String fragment, String paramName, String value) {
    return jdbcClient
        .sql(
            "select id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic from evidence"
                + fragment)
        .param(paramName, value)
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

  @Override
  public VerificationResult verify(String contractId) {
    List<EvidenceChainRow> rows =
        jdbcClient
            .sql(
                """
                select seq, hash, prev_hash, payload
                from evidence
                where contract_id = :contractId
                order by seq
                """)
            .param("contractId", contractId)
            .query(
                (rs, rowNum) ->
                    new EvidenceChainRow(
                        rs.getLong("seq"),
                        rs.getString("hash"),
                        rs.getString("prev_hash"),
                        rs.getString("payload")))
            .list();
    String previous = null;
    boolean valid = true;
    long expectedSeq = 1;
    if (!rows.isEmpty() && rows.get(0).seq() != 1) {
      valid = false;
    }
    for (EvidenceChainRow row : rows) {
      if (!valid) {
        break;
      }
      if (row.seq() != expectedSeq) {
        valid = false;
        break;
      }
      if (!sha256(row.payload()).equalsIgnoreCase(row.hash())) {
        valid = false;
        break;
      }
      if (row.prevHash() != previous && !Objects.equals(row.prevHash(), previous)) {
        valid = false;
        break;
      }
      previous = row.hash();
      expectedSeq++;
    }
    return new VerificationResult(valid, rows.size());
  }

  private static String sha256(String value) {
    synchronized (MD) {
      MD.reset();
      return java.util.HexFormat.of()
          .formatHex(MD.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
  }

  private record EvidenceChainRow(long seq, String hash, String prevHash, String payload) {}
}
