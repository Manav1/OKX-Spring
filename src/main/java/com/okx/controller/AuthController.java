package com.okx.controller;

import com.okx.model.AuthRequest;
import com.okx.service.SessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Dummy auth controller.
 *
 * Credentials are hardcoded in application.yml (auth.username / auth.password).
 * On success a session token is issued via SessionService — this token must be
 * passed as the X-Session-Token header on all subsequent market data requests.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    @Value("${auth.username}")
    private String validUsername;

    @Value("${auth.password}")
    private String validPassword;

    private final SessionService sessionService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody AuthRequest request) {
        log.info("Login attempt: {}", request.getUsername());

        if (!validUsername.equals(request.getUsername())
                || !validPassword.equals(request.getPassword())) {
            log.warn("Login failed for: {}", request.getUsername());
            return ResponseEntity.status(401).body(Map.of(
                    "status",  "error",
                    "message", "Invalid username or password"
            ));
        }

        // createSession() invalidates any existing session for this user first
        String token = sessionService.createSession(request.getUsername());
        log.info("Login successful for: {}", request.getUsername());

        return ResponseEntity.ok(Map.of(
                "status",  "success",
                "message", "Login successful",
                "user",    request.getUsername(),
                "token",   token
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "X-Session-Token", required = false) String token) {
        sessionService.invalidate(token);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Logged out"));
    }
}