package itmo.user.adapter.in.web;

import itmo.user.application.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService service;

    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password", security = {})
    public Mono<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password())
                .map(token -> new TokenResponse(token.accessToken(), token.tokenType(), token.expiresIn()));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the current user profile")
    public Mono<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return service.me(Long.valueOf(jwt.getSubject())).map(UserMapper::toResponse);
    }
}
