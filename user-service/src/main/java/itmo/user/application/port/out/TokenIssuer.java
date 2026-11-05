package itmo.user.application.port.out;

import itmo.user.domain.User;
import itmo.user.application.model.TokenResult;
public interface TokenIssuer { TokenResult issue(User user); }
