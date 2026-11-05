package itmo.tracker.application.port.out;

import java.util.function.Supplier;

public interface UnitOfWork {
    <T> T write(Supplier<T> operation);

    <T> T read(Supplier<T> operation);
}
