package itmo.user.controller;

import itmo.user.dto.ResolveUsersRequest;
import itmo.user.dto.UserReference;
import itmo.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Hidden;
import reactor.core.publisher.Flux;

@Hidden
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {
    private final UserService userService;

    @PostMapping(value = "/resolve", produces = "application/json")
    public Flux<UserReference> resolve(@Valid @RequestBody ResolveUsersRequest request) {
        return userService.resolve(request.ids());
    }
}
