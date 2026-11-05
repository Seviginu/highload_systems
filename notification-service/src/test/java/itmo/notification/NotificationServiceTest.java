package itmo.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import itmo.notification.application.*;
import itmo.notification.application.port.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

class NotificationServiceTest {
    final Notifications store = mock(Notifications.class);
    final EventInbox inbox = mock(EventInbox.class);
    final UnitOfWork transactions =
            new UnitOfWork() {
                public <T> T read(Supplier<T> s) {
                    return s.get();
                }

                public <T> T write(Supplier<T> s) {
                    return s.get();
                }
            };
    final NotificationService service =
            new NotificationService(
                    store, inbox, transactions, Clock.systemUTC(), UUID::randomUUID);

    @Test
    void shouldRejectOversizedApplicationPages() {
        assertThatThrownBy(() -> service.page(1, 0, 51))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void shouldHideMissingOrForeignNotification() {
        assertThatThrownBy(() -> service.markRead(UUID.randomUUID(), 3))
                .isInstanceOf(NotificationNotFound.class);
    }
}
