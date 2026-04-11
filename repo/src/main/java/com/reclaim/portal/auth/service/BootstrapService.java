package com.reclaim.portal.auth.service;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.config.ReclaimProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class BootstrapService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private static final Map<String, String> USER_ROLE_MAP = new HashMap<>();

    static {
        USER_ROLE_MAP.put("admin", "ROLE_ADMIN");
        USER_ROLE_MAP.put("reviewer", "ROLE_REVIEWER");
        USER_ROLE_MAP.put("user", "ROLE_USER");
    }

    private static final Map<String, String> USER_PASSWORD_KEY_MAP = new HashMap<>();

    static {
        USER_PASSWORD_KEY_MAP.put("admin", "admin_password");
        USER_PASSWORD_KEY_MAP.put("reviewer", "reviewer_password");
        USER_PASSWORD_KEY_MAP.put("user", "user_password");
    }

    /**
     * Dev-profile-only bootstrap passwords. These are intentionally weak and only
     * used when running with the {@code dev} profile and no external passwords file.
     * All accounts require a password reset on first login.
     */
    private static final Map<String, String> DEV_PASSWORDS = Map.of(
        "admin_password",    "DevAdmin1!pass",
        "reviewer_password", "DevReviewer1!pass",
        "user_password",     "DevUser1!pass"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReclaimProperties reclaimProperties;
    private final Environment environment;

    public BootstrapService(UserRepository userRepository,
                            RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder,
                            ReclaimProperties reclaimProperties,
                            Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.reclaimProperties = reclaimProperties;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        String passwordsFilePath = reclaimProperties.getBootstrap().getPasswordsFile();

        Map<String, String> passwords;
        if (passwordsFilePath == null || passwordsFilePath.isBlank()) {
            if (isDevProfile()) {
                log.info("No passwords file configured — using dev-profile bootstrap passwords");
                passwords = DEV_PASSWORDS;
            } else {
                return;
            }
        } else {
            passwords = readPasswordsFile(passwordsFilePath);
        }

        for (Map.Entry<String, String> entry : USER_PASSWORD_KEY_MAP.entrySet()) {
            String username = entry.getKey();
            String passwordKey = entry.getValue();

            if (!userRepository.existsByUsername(username)) {
                String rawPassword = passwords.get(passwordKey);
                if (rawPassword == null || rawPassword.isBlank()) {
                    continue;
                }

                String roleName = USER_ROLE_MAP.get(username);
                Optional<Role> roleOpt = roleRepository.findByName(roleName);
                if (roleOpt.isEmpty()) {
                    continue;
                }

                User user = new User();
                user.setUsername(username);
                user.setPasswordHash(passwordEncoder.encode(rawPassword));
                user.setEnabled(true);
                user.setLocked(false);
                user.setForcePasswordReset(true);
                user.setFailedAttempts(0);
                user.setCreatedAt(LocalDateTime.now());
                user.setUpdatedAt(LocalDateTime.now());
                user.setRoles(Set.of(roleOpt.get()));

                userRepository.save(user);
                log.info("Bootstrap user created: username={}, role={}, forcePasswordReset=true",
                         username, roleName);
            }
        }
    }

    private boolean isDevProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private Map<String, String> readPasswordsFile(String filePath) throws IOException {
        Map<String, String> result = new HashMap<>();
        Path path = Path.of(filePath);
        if (!Files.exists(path)) {
            return result;
        }
        for (String line : Files.readAllLines(path)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int idx = line.indexOf('=');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                result.put(key, value);
            }
        }
        return result;
    }
}
