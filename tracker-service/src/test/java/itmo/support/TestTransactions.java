package itmo.support;

import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/** Executes local business logic in unit tests; actual rollback is checked in ITs. */
public class TestTransactions implements TransactionOperations {
    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        return callback.doInTransaction(new SimpleTransactionStatus());
    }
}
