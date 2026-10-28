package com.fcaj.hackathon.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.userdetails.User;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {
    private static final String TEST_SECRET = Base64.getEncoder()
            .encodeToString("test-only-key-material-32-bytes!!".getBytes(StandardCharsets.UTF_8));

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "%%%private-value%%%", "YWJj", "YWJj$"})
    void invalidKeyFailsAtConstructionWithoutExposingItsValue(String secret) {
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> new JwtService(secret, 60000));
        assertTrue(error.getMessage().startsWith("JWT_SECRET"));
        if (secret != null && !secret.isBlank()) {
            assertFalse(error.getMessage().contains(secret));
        }
        assertNull(error.getCause());
    }

    @Test
    void rejectsDecodedKeyShorterThan256Bits() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[31]);
        assertThrows(IllegalStateException.class, () -> new JwtService(shortKey, 60000));
        assertDoesNotThrow(() -> new JwtService(TEST_SECRET, 60000));
    }

    @Test
    void configuredKeySignsAndVerifiesClaimsAcrossServiceRestarts() {
        JwtService issuer = new JwtService(TEST_SECRET, 60000);
        var user = User.withUsername("judge@example.com").password("unused").roles("JUDGE").build();

        String token = issuer.generateToken(user, 42, List.of("JUDGE"));
        JwtService restarted = new JwtService(TEST_SECRET, 60000);
        Claims claims = restarted.extractAllClaims(token);

        assertEquals("judge@example.com", claims.getSubject());
        assertEquals(42, restarted.extractUserId(token));
        assertEquals(List.of("JUDGE"), restarted.extractRoles(token));
        assertTrue(restarted.validateToken(token, user));
        assertFalse(restarted.isTokenExpired(token));
    }

    @Test
    void rejectsTokenSignedWithAnotherKeyOrForAnotherUser() {
        JwtService issuer = new JwtService(TEST_SECRET, 60000);
        var user = User.withUsername("judge@example.com").password("unused").roles("JUDGE").build();
        String token = issuer.generateToken(user, 42, List.of("JUDGE"));
        String otherKey = Base64.getEncoder().encodeToString("another-test-key-material-32-bytes".getBytes(StandardCharsets.UTF_8));
        assertFalse(new JwtService(otherKey, 60000).validateToken(token, user));
        var otherUser = User.withUsername("other@example.com").password("unused").roles("JUDGE").build();
        assertFalse(issuer.validateToken(token, otherUser));
        assertFalse(issuer.validateToken("not-a-token", user));
    }

    @Test
    void rejectsNonPositiveTokenLifetimeAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService(TEST_SECRET, 0));
        assertThrows(IllegalStateException.class, () -> new JwtService(TEST_SECRET, -1));
    }
}
