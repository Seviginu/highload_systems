package itmo.files;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import itmo.files.application.*;
import itmo.files.application.port.*;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.UUID;

class FileServiceTest {
    @Test
    void shouldEnforceBinaryLimitBeforeOpeningTransaction() {
        var files = mock(Files.class);
        var events = mock(FileEvents.class);
        var transactions = mock(UnitOfWork.class);
        var service =
                new FileService(
                        files, events, transactions, Clock.systemUTC(), UUID::randomUUID, 1);
        assertThatThrownBy(() -> service.upload(1, "name", "text/plain", new byte[2]))
                .isInstanceOf(FileTooLarge.class);
        assertThatThrownBy(() -> service.upload(1, "name", "text/plain", null))
                .isInstanceOf(InvalidFile.class);
        verifyNoInteractions(files, events, transactions);
    }
}
