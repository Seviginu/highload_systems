package itmo.user.application.port.out;

import itmo.user.domain.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Set;

public interface Users {
    Mono<Boolean> existsActiveAdminWithPassword();
    Mono<User> findByEmailIgnoreCaseAndDeletedFalse(String email);
    Mono<User> findByIdAndDeletedFalse(Long id);
    Flux<User> findAllByIdInAndDeletedFalse(Set<Long> ids);
    Mono<Boolean> existsByEmailIgnoreCase(String email);
    Mono<Boolean> existsByEmailIgnoreCaseAndIdNot(String email, Long id);
    Mono<Long> countByDeletedFalse();
    Flux<User> findActivePage(int limit, long offset);
    Mono<User> save(User user);
}
