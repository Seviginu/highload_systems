package itmo.user.service;

import itmo.user.dto.LoginRequest;
import itmo.user.dto.TokenResponse;
import itmo.user.dto.UserResponse;
import itmo.user.entity.User;
import itmo.user.mapper.UserMapper;
import itmo.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final UserRepository users;
    private final UserMapper mapper;
    private final PasswordService passwords;
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final long ttl;
    // A BCrypt hash used for the same password check when an account has no usable credentials.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    public AuthService(UserRepository users, UserMapper mapper, PasswordService passwords, JwtEncoder encoder,
                       @Value("${app.jwt.issuer}") String issuer, @Value("${app.jwt.audience}") String audience,
                       @Value("${app.jwt.ttl-seconds:900}") long ttl) {
        if (ttl <= 0) {
            throw new IllegalArgumentException("JWT TTL must be positive");
        }
        this.users = users;
        this.mapper = mapper;
        this.passwords = passwords;
        this.encoder = encoder;
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
    }

    public Mono<TokenResponse> login(LoginRequest request) {
        return users.findByEmailIgnoreCaseAndDeletedFalse(mapper.normalizeEmail(request.email()))
                .flatMap(user -> passwords.matches(request.password(),
                                user.getPasswordHash() == null ? DUMMY_HASH : user.getPasswordHash())
                        .flatMap(matches -> matches && user.getPasswordHash() != null
                                ? Mono.just(issue(user))
                                : Mono.error(new BadCredentialsException("Invalid email or password"))))
                .switchIfEmpty(Mono.defer(() -> passwords.matches(request.password(), DUMMY_HASH)
                        .then(Mono.error(new BadCredentialsException("Invalid email or password")))));
    }

    public Mono<UserResponse> me(Long id) {
        return users.findByIdAndDeletedFalse(id).map(mapper::toResponse)
                .switchIfEmpty(Mono.error(new BadCredentialsException("User is unavailable")));
    }

    private TokenResponse issue(User user) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience))
                .subject(user.getId().toString()).issuedAt(now).expiresAt(now.plusSeconds(ttl))
                .claim("roles", List.of(user.getRole().name())).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl);
    }
}
