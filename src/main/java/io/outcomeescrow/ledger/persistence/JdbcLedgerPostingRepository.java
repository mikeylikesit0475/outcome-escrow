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
    Map<String, Object> params =
        Map.of(
            "id", record.id(),
            "idempotencyKey", record.idempotencyKey(),
            "fromAccountId", record.fromAccountId(),
            "toAccountId", record.toAccountId(),
            "amountMinor", record.amountMinor(),
            "currency", record.currency(),
            "postedAt", java.sql.Timestamp.from(record.postedAt()));
    try {
      jdbcClient
          .sql(
              """
              insert into ledger_postings (id, idempotency_key, from_account_id, to_account_id, amount_minor, currency, posted_at)
              values (:id, :idempotencyKey, :fromAccountId, :toAccountId, :amountMinor, :currency, :postedAt)
              """)
          .params(params)
          .update();
      return true;
    } catch (DuplicateKeyException e) {
      return false;
    }
  }
}
