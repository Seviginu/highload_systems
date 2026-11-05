package itmo.user.application.service;

import itmo.user.application.exception.AuthenticationFailedException;
import itmo.user.application.model.TokenResult;
import itmo.user.application.model.UserView;
import itmo.user.application.port.out.Passwords;
import itmo.user.application.port.out.TokenIssuer;
import itmo.user.application.port.out.Users;
import itmo.user.domain.User;
import reactor.core.publisher.Mono;

public class AuthService {
    private final Users users;
    private final Passwords passwords;
    private final TokenIssuer tokens;

    public AuthService(Users users, Passwords passwords, TokenIssuer tokens) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
    }

    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    public Mono<TokenResult> login(String email, String password) {
        return users.findByEmailIgnoreCaseAndDeletedFalse(User.normalizeEmail(email))
                .flatMap(user -> passwords.matches(password,
                                user.getPasswordHash() == null ? DUMMY_HASH : user.getPasswordHash())
                        .flatMap(matches -> matches && user.getPasswordHash() != null
                                ? Mono.just(tokens.issue(user)) : Mono.error(invalidCredentials())))
                .switchIfEmpty(Mono.defer(() -> passwords.matches(password, DUMMY_HASH)
                        .then(Mono.error(invalidCredentials()))));
    }

    public Mono<UserView> me(Long id) {
        return users.findByIdAndDeletedFalse(id).map(UserView::from)
                .switchIfEmpty(Mono.error(new AuthenticationFailedException("User is unavailable")));
    }

    private AuthenticationFailedException invalidCredentials() {
        return new AuthenticationFailedException("Invalid email or password");
    }
}
