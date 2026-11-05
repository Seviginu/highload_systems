package itmo.files.configuration;

import itmo.files.application.port.UnitOfWork;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringUnitOfWork implements UnitOfWork {
    private final TransactionTemplate writes;
    private final TransactionTemplate reads;

    public SpringUnitOfWork(PlatformTransactionManager manager) {
        writes = new TransactionTemplate(manager);
        reads = new TransactionTemplate(manager);
        reads.setReadOnly(true);
    }

    public <T> T write(Supplier<T> operation) {
        return writes.execute(status -> operation.get());
    }

    public <T> T read(Supplier<T> operation) {
        return reads.execute(status -> operation.get());
    }
}
