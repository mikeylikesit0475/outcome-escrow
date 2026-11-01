package io.outcomeescrow.evidence;

import java.util.List;
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
    String id = record.id();
    if (id == null || id.isBlank()) {
      id = java.util.UUID.randomUUID().toString();
    }
    String prevHash =
        jdbcClient
            .sql(
                """
                select hash
                from evidence
                where contract_id = :contractId
                order by captured_at desc, id desc
                limit 1
                """)
            .param("contractId", record.contractId())
            .query(String.class)
            .optional()
            .orElse(null);
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
            prevHash,
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
    return query(" where outcome_id = :outcomeId", "outcomeId", outcomeId);
  }

  @Override
  public List<EvidenceRecord> findByContract(String contractId) {
    return query(" where contract_id = :contractId", "contractId", contractId);
  }

  private List<EvidenceRecord> query(String whereClause, String paramName, String value) {
    return jdbcClient
        .sql(
            "select id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic from evidence"
                + whereClause
                + " order by captured_at, id")
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
    List<EvidenceRecord> rows =
        jdbcClient
            .sql(
                """
                select id, contract_id, outcome_id, source, source_ref, captured_at, payload, hash, prev_hash, synthetic
                from evidence
                where contract_id = :contractId
                order by captured_at, id
                """)
            .param("contractId", contractId)
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
    String previous = null;
    boolean valid = true;
    for (EvidenceRecord row : rows) {
      if (row.prevHash() != previous
          && !(row.prevHash() != null && row.prevHash().equals(previous))) {
        valid = false;
        break;
      }
      if (isCanonicalHash(row.hash()) && !candidateMatches(row)) {
        valid = false;
        break;
      }
      previous = row.hash();
    }
    // adjust field
    return new VerificationResult(valid, rows.size());
  }

  private boolean isCanonicalHash(String hash) {
    if (hash == null || hash.length() != 64) {
      return false;
    }
    for (int i = 0; i < hash.length(); i++) {
      char c = hash.charAt(i);
      if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
        return false;
      }
    }
    return true;
  }

  private boolean candidateMatches(EvidenceRecord row) {
    String adapter = sha256(row.payload() + row.outcomeId() + row.capturedAt().toString());
    String plain = sha256(row.payload());
    return adapter.equalsIgnoreCase(row.hash()) || plain.equalsIgnoreCase(row.hash());
  }

  private static String sha256(String value) {
    synchronized (MD) {
      MD.reset();
      return java.util.HexFormat.of()
          .formatHex(MD.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
  }
}
