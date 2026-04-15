package com.reclaim.portal.auth.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-style integration tests for {@link CustomUserDetailsService}.
 * Runs against the H2 test database; each test is rolled back via {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomUserDetailsServiceTest {

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // -------------------------------------------------------------------------
    // Test 1: happy-path load
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadUserByUsername() {
        long nonce = System.nanoTime();
        String username = "cuds_user_" + nonce;

        Role userRole     = findOrCreateRole("ROLE_USER");
        Role reviewerRole = findOrCreateRole("ROLE_REVIEWER");

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        user.setEnabled(true);
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole, reviewerRole)));
        userRepository.save(user);

        UserDetails details = customUserDetailsService.loadUserByUsername(username);

        assertThat(details.getUsername()).isEqualTo(username);

        Set<String> authorities = details.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .collect(Collectors.toSet());
        assertThat(authorities).contains("ROLE_USER", "ROLE_REVIEWER");
    }

    // -------------------------------------------------------------------------
    // Test 2: unknown username → UsernameNotFoundException
    // -------------------------------------------------------------------------

    @Test
    void shouldThrowForUnknownUsername() {
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("no_such_user_xyz_" + System.nanoTime()))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // Test 3: disabled flag is reflected
    // -------------------------------------------------------------------------

    @Test
    void shouldReflectDisabledFlag() {
        long nonce = System.nanoTime();
        String username = "cuds_disabled_" + nonce;

        Role userRole = findOrCreateRole("ROLE_USER");

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        user.setEnabled(false);   // <-- disabled
        user.setLocked(false);
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        userRepository.save(user);

        UserDetails details = customUserDetailsService.loadUserByUsername(username);

        assertThat(details.isEnabled()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Test 4: locked flag is reflected
    // -------------------------------------------------------------------------

    @Test
    void shouldReflectLockedFlag() {
        long nonce = System.nanoTime();
        String username = "cuds_locked_" + nonce;

        Role userRole = findOrCreateRole("ROLE_USER");

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        user.setEmail(username + "@example.com");
        user.setEnabled(true);
        user.setLocked(true);    // <-- locked
        user.setForcePasswordReset(false);
        user.setFailedAttempts(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setRoles(new HashSet<>(Set.of(userRole)));
        userRepository.save(user);

        UserDetails details = customUserDetailsService.loadUserByUsername(username);

        assertThat(details.isAccountNonLocked()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Role findOrCreateRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> {
            Role r = new Role();
            r.setName(name);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }
}
