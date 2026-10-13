package com.seal.hackathon.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Builds the HttpOnly cookie that carries the JWT. Single place both
 * AuthController (login/logout) and OAuth2LoginSuccessHandler use, so the
 * cookie attributes (name, secure, sameSite, path) never drift apart.
 */
@Component
public class JwtCookieFactory {

    public static final String COOKIE_NAME = "seal_auth_token";

    @Value("${app.jwt.cookie.secure}")
    private boolean secure;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    // rememberMe=true → persistent cookie (Max-Age = token lifetime).
    // rememberMe=false → session cookie (no Max-Age), gone when the browser closes.
    public ResponseCookie buildAuthCookie(String token, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/");
        if (rememberMe) {
            builder.maxAge(Duration.ofMillis(expirationMs));
        }
        return builder.build();
    }

    public ResponseCookie clearAuthCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }
}
