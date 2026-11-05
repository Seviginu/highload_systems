package itmo.user.application.service;

import itmo.user.application.exception.ConflictException;
import itmo.user.application.exception.ResourceNotFoundException;
import itmo.user.application.model.*;
import itmo.user.application.port.in.UserService;
import itmo.user.application.port.out.Passwords;
import itmo.user.application.port.out.Users;
import itmo.user.domain.User;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Set;

public class UserServiceImpl implements UserService {
    private final Users users;
    private final Passwords passwords;

    public UserServiceImpl(Users users, Passwords passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Override
    public Mono<UserView> create(CreateUserCommand request) {
        return Mono.defer(() -> {
            String email = User.normalizeEmail(request.email());
            return users.existsByEmailIgnoreCase(email)
                    .flatMap(exists -> exists ? Mono.error(emailConflict(email))
                            : passwords.encode(request.password()).flatMap(hash -> {
                                User user = new User(request.name(), email, request.role());
                                user.changePasswordHash(hash);
                                return users.save(user);
                            })).map(UserView::from);
        });
    }

    @Override
    public Mono<UserView> findById(Long id) { return findEntity(id).map(UserView::from); }

    @Override
    public Flux<UserView> findAll(UserPage page) {
        return users.findActivePage(page.size(), page.offset()).map(UserView::from);
    }

    @Override
    public Mono<Long> count() { return users.countByDeletedFalse(); }

    @Override
    public Mono<UserView> update(Long id, UpdateUserCommand request) {
        return Mono.defer(() -> {
            String email = User.normalizeEmail(request.email());
            return findEntity(id).flatMap(user -> users.existsByEmailIgnoreCaseAndIdNot(email, id)
                    .flatMap(exists -> {
                        if (exists) { return Mono.error(emailConflict(email)); }
                        user.update(request.name(), email, request.role());
                        return users.save(user);
                    })).map(UserView::from);
        });
    }

    @Override
    public Mono<Void> delete(Long id) {
        return findEntity(id).flatMap(user -> { user.delete(); return users.save(user); }).then();
    }

    @Override
    public Mono<Void> changePassword(Long id, String password) {
        return findEntity(id).flatMap(user -> passwords.encode(password).flatMap(hash -> {
            user.changePasswordHash(hash);
            return users.save(user);
        })).then();
    }

    @Override
    public Flux<UserReference> resolve(Set<Long> ids) {
        return users.findAllByIdInAndDeletedFalse(ids).map(user -> new UserReference(user.getId(), user.getRole()));
    }

    private Mono<User> findEntity(Long id) {
        return users.findByIdAndDeletedFalse(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("User", id)));
    }

    private ConflictException emailConflict(String email) {
        return new ConflictException("User with email '%s' already exists".formatted(email));
    }
}
