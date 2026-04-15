package com.reclaim.portal.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.BootstrapService;
import com.reclaim.portal.common.config.ReclaimProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BootstrapServiceIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private ReclaimProperties reclaimProperties;

    @Autowired
    private BootstrapService bootstrapService;

    @Test
    void shouldSkipWhenNoFile() throws Exception {
        // With an empty passwords file path, bootstrap should skip gracefully
        String originalPath = reclaimProperties.getBootstrap().getPasswordsFile();
        reclaimProperties.getBootstrap().setPasswordsFile("");

        long countBefore = userRepository.count();

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        long countAfter = userRepository.count();

        assertThat(countAfter).isEqualTo(countBefore);

        // Restore
        reclaimProperties.getBootstrap().setPasswordsFile(originalPath);
    }

    @Test
    void shouldCreateUsers() throws Exception {
        // Ensure roles exist
        ensureRole("ROLE_ADMIN");
        ensureRole("ROLE_REVIEWER");
        ensureRole("ROLE_USER");

        // Create a passwords file with test passwords
        Path pwdFile = tempDir.resolve("test-passwords.properties");
        Files.writeString(pwdFile,
            "admin_password=AdminSecurePass1!\n" +
            "reviewer_password=ReviewerSecurePass1!\n" +
            "user_password=UserSecurePass1!\n"
        );

        // Set passwords file path temporarily
        reclaimProperties.getBootstrap().setPasswordsFile(pwdFile.toString());

        // Use unique usernames by using a dedicated usernames approach
        // The bootstrap service uses fixed usernames: admin, reviewer, user
        // Delete them first if they already exist to allow creation
        userRepository.findByUsername("admin").ifPresent(userRepository::delete);
        userRepository.findByUsername("reviewer").ifPresent(userRepository::delete);
        userRepository.findByUsername("user").ifPresent(userRepository::delete);

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        // At least one of the standard users should now exist
        boolean adminExists = userRepository.existsByUsername("admin");
        boolean reviewerExists = userRepository.existsByUsername("reviewer");
        boolean userExists = userRepository.existsByUsername("user");

        assertThat(adminExists || reviewerExists || userExists).isTrue();

        // Restore
        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    @Test
    void shouldSkipUserWhenPasswordKeyMissing() throws Exception {
        // Only provide admin_password; reviewer and user entries are absent
        ensureRole("ROLE_ADMIN");
        ensureRole("ROLE_REVIEWER");
        ensureRole("ROLE_USER");

        userRepository.findByUsername("admin").ifPresent(userRepository::delete);
        userRepository.findByUsername("reviewer").ifPresent(userRepository::delete);
        userRepository.findByUsername("user").ifPresent(userRepository::delete);

        Path pwdFile = tempDir.resolve("partial-passwords.properties");
        Files.writeString(pwdFile, "admin_password=AdminSecurePass1!\n");

        reclaimProperties.getBootstrap().setPasswordsFile(pwdFile.toString());

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        assertThat(userRepository.existsByUsername("admin")).isTrue();
        // reviewer and user not created because their keys are absent
        assertThat(userRepository.existsByUsername("reviewer")).isFalse();
        assertThat(userRepository.existsByUsername("user")).isFalse();

        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    @Test
    void shouldSkipAlreadyExistingUsers() throws Exception {
        ensureRole("ROLE_ADMIN");
        ensureRole("ROLE_USER");

        // Pre-create the user
        Role adminRole = roleRepository.findByName("ROLE_ADMIN").orElseThrow();
        if (!userRepository.existsByUsername("admin")) {
            com.reclaim.portal.auth.entity.User existingAdmin = new com.reclaim.portal.auth.entity.User();
            existingAdmin.setUsername("admin");
            existingAdmin.setPasswordHash("$2a$12$existinghashedpassword");
            existingAdmin.setEnabled(true);
            existingAdmin.setLocked(false);
            existingAdmin.setForcePasswordReset(false);
            existingAdmin.setFailedAttempts(0);
            existingAdmin.setCreatedAt(LocalDateTime.now());
            existingAdmin.setUpdatedAt(LocalDateTime.now());
            existingAdmin.setRoles(java.util.Set.of(adminRole));
            userRepository.save(existingAdmin);
        }
        String originalHashBeforeRun = userRepository.findByUsername("admin")
            .map(com.reclaim.portal.auth.entity.User::getPasswordHash).orElse(null);

        Path pwdFile = tempDir.resolve("existing-passwords.properties");
        Files.writeString(pwdFile, "admin_password=AnotherPassword1!\n");

        reclaimProperties.getBootstrap().setPasswordsFile(pwdFile.toString());

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        // Password hash should be unchanged since user already existed
        String hashAfterRun = userRepository.findByUsername("admin")
            .map(com.reclaim.portal.auth.entity.User::getPasswordHash).orElse(null);
        assertThat(hashAfterRun).isEqualTo(originalHashBeforeRun);

        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    @Test
    void shouldSkipWhenPasswordsFileDoesNotExist() throws Exception {
        reclaimProperties.getBootstrap().setPasswordsFile("/tmp/nonexistent-passwords-file-xyz.properties");
        long countBefore = userRepository.count();

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        long countAfter = userRepository.count();
        assertThat(countAfter).isEqualTo(countBefore);

        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    @Test
    void shouldSkipCommentAndEmptyLinesInPasswordsFile() throws Exception {
        ensureRole("ROLE_ADMIN");

        userRepository.findByUsername("admin").ifPresent(userRepository::delete);

        Path pwdFile = tempDir.resolve("comments-passwords.properties");
        Files.writeString(pwdFile,
            "# This is a comment\n\n" +
            "admin_password=AdminSecurePass1!\n" +
            "  # indented comment\n"
        );

        reclaimProperties.getBootstrap().setPasswordsFile(pwdFile.toString());

        ApplicationArguments args = new DefaultApplicationArguments();
        bootstrapService.run(args);

        assertThat(userRepository.existsByUsername("admin")).isTrue();

        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    @Test
    void shouldSkipWhenRoleNotFound() throws Exception {
        // Rename ROLE_ADMIN (rather than delete) so findByName("ROLE_ADMIN") returns empty
        // without violating the user_roles FK — prior tests in the same JVM run may have
        // committed users referencing ROLE_ADMIN via the user_roles join table. The
        // @Transactional class rule rolls back the rename at end of test.
        roleRepository.findByName("ROLE_ADMIN").ifPresent(role -> {
            role.setName("ROLE_ADMIN_TEMP_MISSING_" + System.nanoTime());
            roleRepository.save(role);
        });
        userRepository.findByUsername("admin").ifPresent(userRepository::delete);

        Path pwdFile = tempDir.resolve("norole-passwords.properties");
        Files.writeString(pwdFile, "admin_password=AdminSecurePass1!\n");

        reclaimProperties.getBootstrap().setPasswordsFile(pwdFile.toString());

        ApplicationArguments args = new DefaultApplicationArguments();
        // Should not throw — just skip
        bootstrapService.run(args);

        assertThat(userRepository.existsByUsername("admin")).isFalse();

        reclaimProperties.getBootstrap().setPasswordsFile("");
    }

    private void ensureRole(String roleName) {
        roleRepository.findByName(roleName).orElseGet(() -> {
            Role r = new Role();
            r.setName(roleName);
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
    }
}
