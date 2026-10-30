package com.autoscolombia.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {
    private static final String SECRET = "test-signing-key-must-have-at-least-thirty-two-characters";

    private JwtUtil createUtil() {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secret", SECRET);
        ReflectionTestUtils.setField(util, "expirationMs", 60_000L);
        return util;
    }

    private UserDetails principal(String username) {
        return User.withUsername(username).password("hash").roles("OPERADOR").build();
    }

    @Test
    void generateTokenIncludesUsernameClaimsAndExpiry() {
        JwtUtil util = createUtil();
        String token = util.generateToken(principal("op@example.com"), "OPERADOR");
        assertEquals("op@example.com", util.extractUsername(token));
        assertEquals("OPERADOR", util.extractClaim(token, claims -> claims.get("rol", String.class)));
        assertNotNull(util.extractExpiration(token));
    }

    @Test
    void validateTokenRequiresMatchingSubjectAndNotExpired() {
        JwtUtil util = createUtil();
        String token = util.generateToken(principal("op@example.com"), "OPERADOR");
        assertTrue(util.validateToken(token, principal("op@example.com")));
        assertFalse(util.validateToken(token, principal("other@example.com")));
    }

    @Test
    void invalidTokenCannotBeParsed() {
        JwtUtil util = createUtil();
        assertThrows(JwtException.class, () -> util.extractUsername("not.a.jwt"));
    }

    @Test
    void validateTokenReturnsFalseForTamperedToken() {
        JwtUtil util = createUtil();
        String token = util.generateToken(principal("op@example.com"), "OPERADOR");
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(JwtException.class, () -> util.validateToken(tampered, principal("op@example.com")));
    }
}
