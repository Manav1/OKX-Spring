package com.okx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces one active session per username.
 *
 * How it works:
 *   - On login, a UUID token is generated and stored in a map: username → token.
 *   - If the same username logs in again, the old token is invalidated and a
 *     new one is issued. Any WebSocket subscription held by the old token is
 *     therefore orphaned and rejected on the next validate() call.
 *   - Every WebSocket subscribe/unsubscribe request must supply the token.
 *     The token is validated before the operation proceeds. If it doesn't match
 *     the current token for that user, the request is rejected with 401.
 *   - On logout the token is removed entirely.
 *
 * This means: open two browser tabs, log in on both → the first session is
 * immediately invalidated. The first tab's subsequent API calls will be rejected.
 */
@Slf4j
@Service
public class SessionService {

    private final Map<String, String> sessions = new ConcurrentHashMap<>();

    private final Map<String, String> tokenIndex = new ConcurrentHashMap<>();

    public String createSession(String username) {
        // Invalidate any existing session for this user
        String oldToken = sessions.get(username);
        if (oldToken != null) {
            tokenIndex.remove(oldToken);
            log.info("Invalidated previous session for user '{}'", username);
        }

        String token = UUID.randomUUID().toString();
        sessions.put(username, token);
        tokenIndex.put(token, username);
        log.info("Created session for user '{}', token={}", username, token);
        return token;
    }

    public String validate(String token) {
        if (token == null || token.isBlank()) {
            throw new SecurityException("Missing session token");
        }
        String username = tokenIndex.get(token);
        if (username == null) {
            throw new SecurityException("Invalid or expired session token");
        }
        // Cross-check: the user's current token must match
        String currentToken = sessions.get(username);
        if (!token.equals(currentToken)) {
            throw new SecurityException("Session has been superseded by a newer login");
        }
        return username;
    }

    public void invalidate(String token) {
        String username = tokenIndex.remove(token);
        if (username != null) {
            sessions.remove(username);
            log.info("Session invalidated for user '{}'", username);
        }
    }

    public boolean isValid(String token) {
        try { validate(token); return true; }
        catch (SecurityException e) { return false; }
    }
}