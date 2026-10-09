package itmo.user.repository;

import itmo.user.entity.User;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface UserRepository extends ReactiveCrudRepository<User, Long> {
    Mono<User> findByIdAndDeletedFalse(Long id);
    Flux<User> findAllByIdInAndDeletedFalse(Set<Long> ids);
    Mono<Boolean> existsByEmailIgnoreCase(String email);
    Mono<Boolean> existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    Mono<Long> countByDeletedFalse();

    @Query("SELECT * FROM users WHERE deleted = false ORDER BY id LIMIT :limit OFFSET :offset")
    Flux<User> findActivePage(int limit, long offset);
}
