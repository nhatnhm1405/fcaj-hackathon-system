package com.fcaj.hackathon.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * Handles all JWT operations: generation, parsing, and validation.
 * Uses JJWT 0.12.x API.
 *
 * JWT payload contains:
 *   sub      → user email
 *   userId   → user_id from DB
 *   roles    → list of role names (e.g. ["TEAM_LEADER"])
 *   iat      → issued-at
 *   exp      → expiration
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET must be configured as Base64-encoded key material");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET must be valid Base64-encoded key material");
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must decode to at least 32 bytes");
        }
        if (expirationMs <= 0) {
            throw new IllegalStateException("app.jwt.expiration-ms must be positive");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    // ---------------------------------------------------------------
    // Token generation
    // ---------------------------------------------------------------

    public String generateToken(UserDetails userDetails, Integer userId, List<String> roles) {
        return Jwts.builder()
                .subject(userDetails.getUsername())            // email as subject
                .claim("userId", userId)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ---------------------------------------------------------------
    // Token parsing
    // ---------------------------------------------------------------

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Integer extractUserId(String token) {
        return extractAllClaims(token).get("userId", Integer.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractAllClaims(token).get("roles", List.class);
    }

    // ---------------------------------------------------------------
    // Validation
    // ---------------------------------------------------------------

    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    // ---------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }
}
