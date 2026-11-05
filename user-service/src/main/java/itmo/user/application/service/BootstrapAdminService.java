package itmo.user.application.service;

import itmo.user.application.exception.ConflictException;
import itmo.user.application.model.CreateUserCommand;
import itmo.user.application.port.out.Passwords;
import itmo.user.application.port.out.Users;
import itmo.user.domain.User;
import itmo.user.domain.UserRole;
import reactor.core.publisher.Mono;

public class BootstrapAdminService {
    private final Users users;
    private final Passwords passwords;

    public BootstrapAdminService(Users users, Passwords passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    // Configuration is validated lazily, only when bootstrap is needed.
    public Mono<Void> initialize(Mono<CreateUserCommand> configuration) {
        return users.existsActiveAdminWithPassword().flatMap(exists -> {
            if (exists) { return Mono.empty(); }
            return configuration.flatMap(request -> {
                String email = User.normalizeEmail(request.email());
                return users.existsByEmailIgnoreCase(email).flatMap(taken -> {
                    if (taken) { return Mono.error(new ConflictException("Bootstrap admin email is already in use")); }
                    return passwords.encode(request.password()).flatMap(hash -> {
                        var user = new User(request.name(), email, UserRole.ADMIN);
                        user.changePasswordHash(hash);
                        return users.save(user);
                    }).then();
                });
            });
        });
    }
}
