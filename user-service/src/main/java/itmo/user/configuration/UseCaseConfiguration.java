package itmo.user.configuration;

import itmo.user.application.port.in.UserService;
import itmo.user.application.port.out.*;
import itmo.user.application.service.AuthService;
import itmo.user.application.service.BootstrapAdminService;
import itmo.user.application.service.UserServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UseCaseConfiguration {
    @Bean
    UserService userService(Users users, Passwords passwords) {
        return new ManagedUserService(new UserServiceImpl(users, passwords));
    }
    @Bean
    BootstrapAdminService bootstrapAdminService(Users users, Passwords passwords) {
        return new BootstrapAdminService(users, passwords);
    }
    @Bean
    AuthService authService(Users users, Passwords passwords, TokenIssuer tokens) {
        return new AuthService(users, passwords, tokens);
    }
}
