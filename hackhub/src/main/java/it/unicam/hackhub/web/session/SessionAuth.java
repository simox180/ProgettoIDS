package it.unicam.hackhub.web.session;

public final class SessionAuth {
    private SessionAuth() {
    }

    public static long requireUserId(InMemorySessionStore store, String token) {
        SessionPrincipal principal = requirePrincipal(store, token);
        if (principal.getType() == SessionPrincipal.ProfileType.USER) {
            return principal.getId();
        }
        throw new ForbiddenException("Non autorizzato");
    }

    public static long requireStaffId(InMemorySessionStore store, String token) {
        SessionPrincipal principal = requirePrincipal(store, token);
        if (principal.getType() == SessionPrincipal.ProfileType.STAFF) {
            return principal.getId();
        }
        throw new ForbiddenException("Non autorizzato");
    }

    private static SessionPrincipal requirePrincipal(InMemorySessionStore store, String token) {
        return store.find(token)
                .orElseThrow(() -> new UnauthenticatedException("Non autenticato"));
    }
}
