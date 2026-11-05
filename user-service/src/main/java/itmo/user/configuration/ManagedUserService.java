package itmo.user.configuration;

import itmo.user.application.model.*;
import itmo.user.application.port.in.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Set;

@RequiredArgsConstructor
public class ManagedUserService implements UserService {
    private final UserService delegate;
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserView> create(CreateUserCommand request) { return delegate.create(request); }
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserView> findById(Long id) { return delegate.findById(id); }
    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Flux<UserView> findAll(UserPage page) { return delegate.findAll(page); }
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Long> count() { return delegate.count(); }
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<UserView> update(Long id, UpdateUserCommand request) { return delegate.update(id, request); }
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> delete(Long id) { return delegate.delete(id); }
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<Void> changePassword(Long id, String password) { return delegate.changePassword(id, password); }
    @Override
    public Flux<UserReference> resolve(Set<Long> ids) { return delegate.resolve(ids); }
}
