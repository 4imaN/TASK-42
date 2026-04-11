package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.users.dto.MaskedUserProfileDto;
import com.reclaim.portal.users.dto.UserProfileDto;
import com.reclaim.portal.users.service.UserService;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_ADMIN");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setUsername("user_svc_test_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        testUser.setEmail("testuser@example.com");
        testUser.setPhone("5551234567");
        testUser.setFullName("Alice Smith");
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(userRole)));
        testUser = userRepository.save(testUser);

        adminUser = new User();
        adminUser.setUsername("admin_svc_test_" + System.nanoTime());
        adminUser.setPasswordHash(passwordEncoder.encode("AdminPass1!"));
        adminUser.setEmail("admin@example.com");
        adminUser.setPhone("5559876543");
        adminUser.setFullName("Bob Admin");
        adminUser.setEnabled(true);
        adminUser.setLocked(false);
        adminUser.setForcePasswordReset(false);
        adminUser.setFailedAttempts(0);
        adminUser.setCreatedAt(LocalDateTime.now());
        adminUser.setUpdatedAt(LocalDateTime.now());
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);
    }

    @Test
    void shouldGetFullProfile() {
        UserProfileDto profile = userService.getUserProfile(testUser.getId());

        assertThat(profile).isNotNull();
        assertThat(profile.getId()).isEqualTo(testUser.getId());
        assertThat(profile.getUsername()).isEqualTo(testUser.getUsername());
        assertThat(profile.getEmail()).isEqualTo("testuser@example.com");
        assertThat(profile.getFullName()).isEqualTo("Alice Smith");
        assertThat(profile.getPhone()).isEqualTo("5551234567");
        assertThat(profile.getRoles()).contains("ROLE_USER");
    }

    @Test
    void shouldGetMaskedProfile() {
        MaskedUserProfileDto masked = userService.getMaskedUserProfile(testUser.getId());

        assertThat(masked).isNotNull();
        assertThat(masked.getId()).isEqualTo(testUser.getId());
        // Email should be masked: "te***@example.com"
        assertThat(masked.getMaskedEmail()).startsWith("te");
        assertThat(masked.getMaskedEmail()).contains("***@");
        // Phone should be masked: "***4567"
        assertThat(masked.getMaskedPhone()).startsWith("***");
        assertThat(masked.getMaskedPhone()).endsWith("4567");
        // Full name should be masked: "A***"
        assertThat(masked.getMaskedFullName()).startsWith("A");
        assertThat(masked.getMaskedFullName()).contains("***");
    }

    @Test
    void shouldRevealPii() {
        UserProfileDto revealed = userService.revealPii(adminUser.getId(), testUser.getId(), "Audit reason");

        assertThat(revealed).isNotNull();
        assertThat(revealed.getEmail()).isEqualTo("testuser@example.com");
        assertThat(revealed.getPhone()).isEqualTo("5551234567");
        assertThat(revealed.getFullName()).isEqualTo("Alice Smith");
    }
}
