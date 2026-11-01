package io.outcomeescrow.ledger.persistence;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcLedgerPostingRepository implements LedgerPostingRepository {

  private final JdbcClient jdbcClient;

  public JdbcLedgerPostingRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  @Override
  public void ensureAccount(String accountId, String currency) {
    jdbcClient
        .sql(
            """
            insert into ledger_accounts (id, currency)
            values (:id, :currency)
            on conflict (id) do nothing
            """)
        .param("id", accountId)
        .param("currency", currency)
        .update();
  }

  @Override
  public boolean append(LedgerPostingRecord record) {
    ensureAccount(record.fromAccountId(), record.currency());
    ensureAccount(record.toAccountId(), record.currency());
    Map<String, Object> params = new java.util.HashMap<>();
    params.put("id", record.id());
    params.put("idempotencyKey", record.idempotencyKey());
    params.put("fromAccountId", record.fromAccountId());
    params.put("toAccountId", record.toAccountId());
    params.put("amountMinor", record.amountMinor());
    params.put("currency", record.currency());
    params.put("postedAt", java.sql.Timestamp.from(record.postedAt()));
    params.put("verdictId", record.verdictId());
    params.put("outcomeId", record.outcomeId());
    try {
      jdbcClient
          .sql(
              """
              insert into ledger_postings (id, idempotency_key, from_account_id, to_account_id, amount_minor, currency, posted_at, verdict_id, outcome_id)
              values (:id, :idempotencyKey, :fromAccountId, :toAccountId, :amountMinor, :currency, :postedAt, :verdictId, :outcomeId)
              """)
          .params(params)
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }
}
