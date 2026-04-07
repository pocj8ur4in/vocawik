package com.vocawik.security.internal;

import com.vocawik.domain.user.User;
import com.vocawik.repository.user.UserRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Resolves internal API authentication against a configured shared token and user id. */
@Service
@RequiredArgsConstructor
public class InternalApiAuthenticationService {

    private final UserRepository userRepository;

    @Value("${security.internal-api.token:}")
    private String internalApiToken;

    @Value("${security.internal-api.user-id:2}")
    private long internalApiUserId;

    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (internalApiToken == null || internalApiToken.isBlank()) {
            return Optional.empty();
        }
        if (!internalApiToken.equals(token)) {
            return Optional.empty();
        }

        return userRepository.findById(internalApiUserId).filter(user -> !user.isDeleted());
    }
}
