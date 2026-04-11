package com.reclaim.portal.service;

import com.reclaim.portal.auth.dto.AuthResponse;
import com.reclaim.portal.auth.entity.RefreshToken;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RefreshTokenRepository;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.AuthService;
import com.reclaim.portal.auth.service.JwtService;
import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private ReclaimProperties reclaimProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Ensure ROLE_USER exists
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setDescription("Standard user role");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        // Create a test user
        testUser = new User();
        testUser.setUsername("testuser_auth_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        testUser.setEmail("testuser@example.com");
        testUser.setFullName("Test User");
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(role)));
        testUser = userRepository.save(testUser);
    }

    @Test
    void shouldLoginSuccessfully() {
        AuthResponse response = authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getRefreshToken()).isNotBlank();
        assertThat(response.isForcePasswordReset()).isFalse();
    }

    @Test
    void shouldLockAfterFiveFailedAttempts() {
        String username = testUser.getUsername();

        for (int i = 0; i < 5; i++) {
            try {
                authService.login(username, "WrongPassword!", "127.0.0.1");
            } catch (BusinessRuleException ignored) {
                // expected
            }
        }

        User locked = userRepository.findByUsername(username).orElseThrow();
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.getLockedUntil()).isNotNull();
    }

    @Test
    void shouldRejectLoginWhenLocked() {
        // Force-lock the account
        testUser.setLocked(true);
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        userRepository.save(testUser);

        assertThatThrownBy(() ->
            authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("locked");
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // Generate and store a refresh token, then sleep >1s so the next generated
        // token has a different issuedAt timestamp (JWT issuedAt has second precision)
        // avoiding the unique hash constraint violation on refresh_tokens
        String refreshTokenStr = jwtService.generateRefreshToken(testUser);
        String tokenHash = sha256Hex(refreshTokenStr);

        RefreshToken stored = new RefreshToken();
        stored.setUserId(testUser.getId());
        stored.setTokenHash(tokenHash);
        stored.setExpiresAt(LocalDateTime.now().plusDays(7));
        stored.setRevoked(false);
        stored.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.saveAndFlush(stored);

        // Refresh tokens now include a UUID jti, so no sleep needed for uniqueness
        AuthResponse refreshResponse = authService.refresh(refreshTokenStr);

        assertThat(refreshResponse).isNotNull();
        assertThat(refreshResponse.getAccessToken()).isNotBlank();
        assertThat(refreshResponse.getRefreshToken()).isNotBlank();
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    @Test
    void shouldChangePassword() {
        // Change the password
        authService.changePassword(testUser.getId(), "TestPassword1!", "NewSecurePass2@");

        // Old password should no longer work
        assertThatThrownBy(() ->
            authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1")
        ).isInstanceOf(BusinessRuleException.class);

        // New password should work
        AuthResponse response = authService.login(testUser.getUsername(), "NewSecurePass2@", "127.0.0.1");
        assertThat(response.getAccessToken()).isNotBlank();
    }

    @Test
    void shouldRejectWeakPasswordTooShort() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "TestPassword1!", "Short1!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("12 characters");
    }

    @Test
    void shouldRejectPasswordWithNoUppercase() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "TestPassword1!", "alllowercase1!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("uppercase");
    }

    @Test
    void shouldRejectPasswordWithNoLowercase() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "TestPassword1!", "ALLUPPERCASE1!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("lowercase");
    }

    @Test
    void shouldRejectPasswordWithNoDigit() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "TestPassword1!", "NoDigitHereABC!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("digit");
    }

    @Test
    void shouldRejectPasswordWithNoSpecialChar() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "TestPassword1!", "NoSpecialChar12")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("special character");
    }

    @Test
    void shouldRejectInvalidCredentials() {
        assertThatThrownBy(() ->
            authService.login(testUser.getUsername(), "WrongPassword99!", "127.0.0.1")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("Invalid");
    }

    @Test
    void shouldIncrementFailedAttemptsOnWrongPassword() {
        int initialAttempts = testUser.getFailedAttempts();

        try {
            authService.login(testUser.getUsername(), "WrongPassword!", "10.0.0.1");
        } catch (BusinessRuleException ignored) {
            // expected
        }

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(updated.getFailedAttempts()).isEqualTo(initialAttempts + 1);
    }

    @Test
    void shouldResetFailedAttemptsAfterSuccessfulLogin() {
        // Trigger some failed attempts first
        for (int i = 0; i < 2; i++) {
            try {
                authService.login(testUser.getUsername(), "BadPass!", "127.0.0.1");
            } catch (BusinessRuleException ignored) {}
        }

        // Now login successfully
        authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");

        User updated = userRepository.findByUsername(testUser.getUsername()).orElseThrow();
        assertThat(updated.getFailedAttempts()).isEqualTo(0);
        assertThat(updated.isLocked()).isFalse();
    }

    @Test
    void shouldLogoutByRevokingRefreshToken() throws Exception {
        // Login to get a valid token pair
        AuthResponse response = authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");
        String refreshTokenStr = response.getRefreshToken();

        // Logout should revoke the token
        authService.logout(refreshTokenStr);

        // Refresh after logout should fail
        assertThatThrownBy(() -> authService.refresh(refreshTokenStr))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void shouldLogoutGracefullyWhenTokenNotStored() {
        // Logout with a non-existent token should not throw
        authService.logout("non-existent-token-that-has-no-hash-match");
    }

    @Test
    void shouldRejectRefreshWithRevokedToken() throws Exception {
        String refreshTokenStr = jwtService.generateRefreshToken(testUser);
        String tokenHash = sha256Hex(refreshTokenStr);

        RefreshToken stored = new RefreshToken();
        stored.setUserId(testUser.getId());
        stored.setTokenHash(tokenHash);
        stored.setExpiresAt(LocalDateTime.now().plusDays(7));
        stored.setRevoked(true); // already revoked
        stored.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> authService.refresh(refreshTokenStr))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("revoked");
    }

    @Test
    void shouldRejectRefreshWithExpiredToken() throws Exception {
        String refreshTokenStr = jwtService.generateRefreshToken(testUser);
        String tokenHash = sha256Hex(refreshTokenStr);

        RefreshToken stored = new RefreshToken();
        stored.setUserId(testUser.getId());
        stored.setTokenHash(tokenHash);
        stored.setExpiresAt(LocalDateTime.now().minusDays(1)); // expired
        stored.setRevoked(false);
        stored.setCreatedAt(LocalDateTime.now());
        refreshTokenRepository.saveAndFlush(stored);

        assertThatThrownBy(() -> authService.refresh(refreshTokenStr))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void shouldRejectRefreshWithInvalidJwt() {
        assertThatThrownBy(() -> authService.refresh("not.a.valid.jwt"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("Invalid refresh token");
    }

    @Test
    void shouldRejectChangePasswordWithWrongOldPassword() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "WrongOldPass1!", "NewSecurePass2@")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("incorrect");
    }

    @Test
    void shouldSetForcePasswordResetFalseAfterChange() {
        // Give user a forced reset flag
        testUser.setForcePasswordReset(true);
        userRepository.save(testUser);

        authService.changePassword(testUser.getId(), "TestPassword1!", "NewSecurePass2@");

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.isForcePasswordReset()).isFalse();
    }

    @Test
    void shouldLoginWithForcePasswordResetTrue() {
        testUser.setForcePasswordReset(true);
        userRepository.save(testUser);

        AuthResponse response = authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");

        assertThat(response.isForcePasswordReset()).isTrue();
    }

    @Test
    void shouldRejectLoginWhenDisabled() {
        testUser.setEnabled(false);
        userRepository.save(testUser);

        assertThatThrownBy(() ->
            authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("disabled");
    }

    @Test
    void shouldRejectRefreshWhenDisabled() throws Exception {
        // Login while enabled to get a valid refresh token
        AuthResponse response = authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");
        String refreshTokenStr = response.getRefreshToken();

        // Disable the account after token issuance
        testUser.setEnabled(false);
        testUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(testUser);

        // Refresh should now be rejected
        assertThatThrownBy(() -> authService.refresh(refreshTokenStr))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("disabled");
    }

    @Test
    void shouldRejectRefreshWhenLocked() throws Exception {
        // Login while unlocked to get a valid refresh token
        AuthResponse response = authService.login(testUser.getUsername(), "TestPassword1!", "127.0.0.1");
        String refreshTokenStr = response.getRefreshToken();

        // Lock the account after token issuance
        testUser.setLocked(true);
        testUser.setLockedUntil(LocalDateTime.now().plusMinutes(15));
        testUser.setUpdatedAt(LocalDateTime.now());
        userRepository.save(testUser);

        // Refresh should now be rejected
        assertThatThrownBy(() -> authService.refresh(refreshTokenStr))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("locked");
    }
}
