package itmo.notification.application.port;

import java.util.function.Supplier;

public interface UnitOfWork {
    <T> T read(Supplier<T> operation);

    <T> T write(Supplier<T> operation);
}
