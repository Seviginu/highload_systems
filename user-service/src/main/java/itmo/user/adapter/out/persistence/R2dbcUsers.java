package itmo.user.adapter.out.persistence;

import itmo.user.application.exception.ConflictException;
import itmo.user.application.port.out.Users;
import itmo.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class R2dbcUsers implements Users {
    private final UserRepository repository;

    @Override
    public Mono<Boolean> existsActiveAdminWithPassword() {
        return repository.existsByRoleAndDeletedFalseAndPasswordHashIsNotNull(itmo.user.domain.UserRole.ADMIN);
    }
    @Override
    public Mono<User> findByEmailIgnoreCaseAndDeletedFalse(String email) {
        return repository.findByEmailIgnoreCaseAndDeletedFalse(email).map(this::toDomain);
    }
    @Override
    public Mono<User> findByIdAndDeletedFalse(Long id) {
        return repository.findByIdAndDeletedFalse(id).map(this::toDomain);
    }
    @Override
    public Flux<User> findAllByIdInAndDeletedFalse(Set<Long> ids) {
        return repository.findAllByIdInAndDeletedFalse(ids).map(this::toDomain);
    }
    @Override
    public Mono<Boolean> existsByEmailIgnoreCase(String email) { return repository.existsByEmailIgnoreCase(email); }
    @Override
    public Mono<Boolean> existsByEmailIgnoreCaseAndIdNot(String email, Long id) {
        return repository.existsByEmailIgnoreCaseAndIdNot(email, id);
    }
    @Override
    public Mono<Long> countByDeletedFalse() { return repository.countByDeletedFalse(); }
    @Override
    public Flux<User> findActivePage(int limit, long offset) {
        return repository.findActivePage(limit, offset).map(this::toDomain);
    }
    @Override
    public Mono<User> save(User user) {
        return Mono.defer(() -> repository.save(toEntity(user))).map(this::toDomain)
                .onErrorMap(OptimisticLockingFailureException.class, error -> new ConflictException(
                        "User with id '%d' was modified by another request".formatted(user.getId())))
                .onErrorMap(DataIntegrityViolationException.class, error -> {
                    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                        if (cause.getMessage() != null && cause.getMessage().contains("uq_users_email_lower")) {
                            return new ConflictException("User with email '%s' already exists".formatted(user.getEmail()));
                        }
                    }
                    return error;
                });
    }

    private User toDomain(UserEntity user) {
        return new User(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getPasswordHash(),
                user.getCreatedAt(), user.getUpdatedAt(), user.isDeleted(), user.getVersion());
    }
    private UserEntity toEntity(User user) {
        return new UserEntity(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.getPasswordHash(),
                user.getCreatedAt(), user.getUpdatedAt(), user.isDeleted(), user.getVersion());
    }
}
