package itmo.support;
import itmo.tracker.application.port.out.UnitOfWork;
import java.util.function.Supplier;
/** Unit tests run directly; real rollback is checked with PostgreSQL in ITs. */
public class TestTransactions implements UnitOfWork {
    public <T> T write(Supplier<T> operation) { return operation.get(); }
    public <T> T read(Supplier<T> operation) { return operation.get(); }
}
