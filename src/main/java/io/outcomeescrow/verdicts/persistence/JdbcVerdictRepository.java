package io.outcomeescrow.verdicts.persistence;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcVerdictRepository implements VerdictRepository {
  private final JdbcClient jdbcClient;

  public JdbcVerdictRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void save(VerdictRecord record) {
    if (record == null) {
      throw new IllegalArgumentException("record is required");
    }
    jdbcClient
        .sql(
            """
            insert into verdicts (
              id, outcome_id, score, status, rule_version, evidence_head_hash, evaluated_at,
              tier_minimum_score, tier_payout_basis_points, evidence_count, explanation
            ) values (
              :id, :outcomeId, :score, :status, :ruleVersion, :evidenceHeadHash, :evaluatedAt,
              :tierMinimumScore, :tierPayoutBasisPoints, :evidenceCount, :explanation
            )
            """)
        .param("id", record.id())
        .param("outcomeId", record.outcomeId())
        .param("score", record.score())
        .param("status", record.status())
        .param("ruleVersion", record.ruleVersion())
        .param("evidenceHeadHash", record.evidenceHeadHash())
        .param("evaluatedAt", java.sql.Timestamp.from(record.evaluatedAt()))
        .param("tierMinimumScore", record.tierMinimumScore())
        .param("tierPayoutBasisPoints", record.tierPayoutBasisPoints())
        .param("evidenceCount", record.evidenceCount())
        .param("explanation", record.explanation())
        .update();
  }

  @Override
  public VerdictRecord findById(String id) {
    return jdbcClient
        .sql(
            """
            select id, outcome_id, score, status, rule_version, evidence_head_hash, evaluated_at,
                   tier_minimum_score, tier_payout_basis_points, evidence_count, explanation
            from verdicts where id = :id
            """)
        .param("id", id)
        .query(
            (rs, row) ->
                new VerdictRecord(
                    rs.getString("id"),
                    rs.getString("outcome_id"),
                    rs.getInt("score"),
                    rs.getString("status"),
                    rs.getString("rule_version"),
                    rs.getString("evidence_head_hash"),
                    rs.getTimestamp("evaluated_at").toInstant(),
                    rs.getInt("evidence_count"),
                    rs.getInt("tier_minimum_score"),
                    rs.getInt("tier_payout_basis_points"),
                    rs.getString("explanation")))
        .optional()
        .orElse(null);
  }

  @Override
  public List<VerdictRecord> findByOutcome(String outcomeId) {
    return jdbcClient
        .sql(
            """
            select id, outcome_id, score, status, rule_version, evidence_head_hash, evaluated_at,
                   tier_minimum_score, tier_payout_basis_points, evidence_count, explanation
            from verdicts where outcome_id = :outcomeId order by evaluated_at desc
            """)
        .param("outcomeId", outcomeId)
        .query(rowMapper())
        .list();
  }

  @Override
  public List<VerdictRecord> findByContract(String contractId) {
    return jdbcClient
        .sql(
            """
            select v.id, v.outcome_id, v.score, v.status, v.rule_version, v.evidence_head_hash,
                   v.evaluated_at, v.tier_minimum_score, v.tier_payout_basis_points,
                   v.evidence_count, v.explanation
            from verdicts v
            join outcomes o on o.id = v.outcome_id
            where o.contract_id = :contractId
            order by v.evaluated_at desc
            """)
        .param("contractId", contractId)
        .query(rowMapper())
        .list();
  }

  private org.springframework.jdbc.core.RowMapper<VerdictRecord> rowMapper() {
    return (rs, row) ->
        new VerdictRecord(
            rs.getString("id"),
            rs.getString("outcome_id"),
            rs.getInt("score"),
            rs.getString("status"),
            rs.getString("rule_version"),
            rs.getString("evidence_head_hash"),
            rs.getTimestamp("evaluated_at").toInstant(),
            rs.getInt("evidence_count"),
            rs.getInt("tier_minimum_score"),
            rs.getInt("tier_payout_basis_points"),
            rs.getString("explanation"));
  }
}
