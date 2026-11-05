package itmo.user.adapter.out.persistence;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UserBeforeConvertCallback implements BeforeConvertCallback<UserEntity> {
    private final Validator validator;

    @Override
    public Mono<UserEntity> onBeforeConvert(UserEntity user, SqlIdentifier table) {
        return Mono.fromSupplier(() -> {
            var violations = validator.validate(user);
            if (!violations.isEmpty()) {
                throw new ConstraintViolationException(violations);
            }
            user.markSaved(Instant.now());
            return user;
        });
    }
}
