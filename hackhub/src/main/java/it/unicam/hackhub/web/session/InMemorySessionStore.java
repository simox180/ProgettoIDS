package it.unicam.hackhub.web.session;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class InMemorySessionStore {
    private final Map<String, SessionPrincipal> sessions = new HashMap<>();

    public String create(SessionPrincipal principal) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, principal);
        return token;
    }

    public Optional<SessionPrincipal> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token));
    }

    public void invalidate(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.remove(token);
    }
}
