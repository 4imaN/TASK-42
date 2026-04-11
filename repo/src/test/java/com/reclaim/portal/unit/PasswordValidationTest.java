package com.reclaim.portal.unit;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.AuthService;
import com.reclaim.portal.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Pure validation tests for password strength rules exercised via AuthService.changePassword.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PasswordValidationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setUsername("pwtest_user_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("CurrentPassword1!"));
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
    void shouldRejectPasswordTooShort() {
        // 11 chars — one short of the 12-char minimum
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "Short1!abcd")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("12 characters");
    }

    @Test
    void shouldRejectPasswordNoUppercase() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "alllowercase1!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("uppercase");
    }

    @Test
    void shouldRejectPasswordNoLowercase() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "ALLUPPERCASE1!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("lowercase");
    }

    @Test
    void shouldRejectPasswordNoDigit() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "NoDigitHereABC!")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("digit");
    }

    @Test
    void shouldRejectPasswordNoSpecialCharacter() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "NoSpecialChar12")
        ).isInstanceOf(BusinessRuleException.class)
         .hasMessageContaining("special character");
    }

    @Test
    void shouldAcceptValidPassword() {
        // Valid: >= 12 chars, upper, lower, digit, special
        assertThatNoException().isThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", "ValidP@ssword1!")
        );
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() ->
            authService.changePassword(testUser.getId(), "CurrentPassword1!", null)
        ).isInstanceOf(BusinessRuleException.class);
    }
}
