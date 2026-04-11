package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.common.config.ReclaimProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for JwtService — no Spring context needed.
 * ReclaimProperties is constructed directly to avoid Mockito inline-mocking
 * restrictions on Java 21+.
 */
class JwtServiceTest {

    // Must be at least 256 bits (32 bytes) for HMAC-SHA-256
    private static final String JWT_SECRET =
        "test-jwt-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm-at-least-256-bits";
    private static final String REFRESH_SECRET =
        "test-refresh-secret-key-that-is-long-enough-for-hmac-sha-256-algorithm-at-least-256-bits";

    private JwtService jwtService;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtService = buildJwtService(30, 7);

        Role role = new Role();
        role.setName("ROLE_USER");

        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setCreatedAt(LocalDateTime.now());
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        testUser.setRoles(roles);
    }

    private JwtService buildJwtService(int accessTokenMinutes, int refreshTokenDays) {
        ReclaimProperties props = new ReclaimProperties();
        ReclaimProperties.Security sec = new ReclaimProperties.Security();
        sec.setJwtSecret(JWT_SECRET);
        sec.setRefreshSecret(REFRESH_SECRET);
        sec.setAccessTokenMinutes(accessTokenMinutes);
        sec.setRefreshTokenDays(refreshTokenDays);
        props.setSecurity(sec);
        return new JwtService(props);
    }

    @Test
    void shouldGenerateAccessToken() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        // JWT tokens have 3 parts separated by dots
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void shouldExtractUsername() {
        String token = jwtService.generateAccessToken(testUser);
        String extracted = jwtService.extractUsername(token);

        assertThat(extracted).isEqualTo("testuser");
    }

    @Test
    void shouldValidateValidToken() {
        String token = jwtService.generateAccessToken(testUser);

        boolean valid = jwtService.isTokenValid(token);

        assertThat(valid).isTrue();
    }

    @Test
    void shouldDetectInvalidToken() {
        boolean valid = jwtService.isTokenValid("this.is.notvalid");

        assertThat(valid).isFalse();
    }

    @Test
    void shouldDetectTamperedToken() {
        String token = jwtService.generateAccessToken(testUser);
        // Tamper with the signature (last segment)
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + ".invalidsignature";

        assertThat(jwtService.isTokenValid(tampered)).isFalse();
    }

    @Test
    void shouldGenerateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(refreshToken).isNotBlank();
        assertThat(refreshToken.split("\\.")).hasSize(3);
    }

    @Test
    void shouldValidateRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(jwtService.isRefreshTokenValid(refreshToken)).isTrue();
    }

    @Test
    void shouldExtractUsernameFromRefreshToken() {
        String refreshToken = jwtService.generateRefreshToken(testUser);
        String extracted = jwtService.extractUsernameFromRefreshToken(refreshToken);

        assertThat(extracted).isEqualTo("testuser");
    }

    @Test
    void shouldDetectExpiredToken() {
        // Build a JwtService instance with -1 minute expiry (already expired)
        JwtService expiredJwtService = buildJwtService(-1, 7);

        String expiredToken = expiredJwtService.generateAccessToken(testUser);

        // Token should be invalid since it's already expired
        assertThat(expiredJwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    void shouldRejectRefreshTokenUsedAsAccessToken() {
        // A refresh token should not validate as access token (different signing key)
        String refreshToken = jwtService.generateRefreshToken(testUser);

        assertThat(jwtService.isTokenValid(refreshToken)).isFalse();
    }
}
