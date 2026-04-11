package com.reclaim.portal.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Validates that required security secrets are set at startup.
 * Fails fast in non-test/non-dev profiles to prevent running with empty secrets.
 */
@Component
public class SecuritySecretsValidator {

    private static final Logger log = LoggerFactory.getLogger(SecuritySecretsValidator.class);

    private final ReclaimProperties reclaimProperties;
    private final Environment environment;

    public SecuritySecretsValidator(ReclaimProperties reclaimProperties, Environment environment) {
        this.reclaimProperties = reclaimProperties;
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void validateSecrets() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        if (activeProfiles.contains("test") || activeProfiles.contains("dev")) {
            log.info("Security secrets validation skipped for profile: {}", activeProfiles);
            return;
        }

        ReclaimProperties.Security security = reclaimProperties.getSecurity();
        StringBuilder errors = new StringBuilder();

        if (isBlankOrMissing(security.getJwtSecret())) {
            errors.append("  - RECLAIM_JWT_SECRET is not set\n");
        }
        if (isBlankOrMissing(security.getRefreshSecret())) {
            errors.append("  - RECLAIM_REFRESH_SECRET is not set\n");
        }
        if (isBlankOrMissing(security.getEncryptionKey())) {
            errors.append("  - RECLAIM_ENCRYPTION_KEY is not set\n");
        }

        if (!errors.isEmpty()) {
            String message = "Required security secrets are missing. "
                    + "Set environment variables or use the 'dev' profile for local development:\n"
                    + errors
                    + "See README.md for configuration details.";
            log.error(message);
            throw new IllegalStateException(message);
        }

        log.info("Security secrets validation passed");
    }

    private boolean isBlankOrMissing(String value) {
        return value == null || value.isBlank();
    }
}
