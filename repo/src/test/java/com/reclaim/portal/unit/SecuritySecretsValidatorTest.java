package com.reclaim.portal.unit;

import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.config.SecuritySecretsValidator;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for SecuritySecretsValidator: fail-fast when secrets are missing.
 */
class SecuritySecretsValidatorTest {

    @Test
    void shouldFailWhenJwtSecretIsMissing() {
        ReclaimProperties props = new ReclaimProperties();
        props.getSecurity().setJwtSecret("");
        props.getSecurity().setRefreshSecret("valid-secret-here");
        props.getSecurity().setEncryptionKey("valid-key");

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        SecuritySecretsValidator validator = new SecuritySecretsValidator(props, env);

        assertThatThrownBy(validator::validateSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECLAIM_JWT_SECRET");
    }

    @Test
    void shouldFailWhenAllSecretsAreMissing() {
        ReclaimProperties props = new ReclaimProperties();
        props.getSecurity().setJwtSecret(null);
        props.getSecurity().setRefreshSecret(null);
        props.getSecurity().setEncryptionKey(null);

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        SecuritySecretsValidator validator = new SecuritySecretsValidator(props, env);

        assertThatThrownBy(validator::validateSecrets)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RECLAIM_JWT_SECRET")
                .hasMessageContaining("RECLAIM_REFRESH_SECRET")
                .hasMessageContaining("RECLAIM_ENCRYPTION_KEY");
    }

    @Test
    void shouldSkipValidationForTestProfile() {
        ReclaimProperties props = new ReclaimProperties();
        // All secrets are null/empty
        props.getSecurity().setJwtSecret(null);
        props.getSecurity().setRefreshSecret(null);
        props.getSecurity().setEncryptionKey(null);

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"test"});

        SecuritySecretsValidator validator = new SecuritySecretsValidator(props, env);

        // Should not throw — test profile is exempt
        validator.validateSecrets();
    }

    @Test
    void shouldSkipValidationForDevProfile() {
        ReclaimProperties props = new ReclaimProperties();
        props.getSecurity().setJwtSecret(null);
        props.getSecurity().setRefreshSecret(null);
        props.getSecurity().setEncryptionKey(null);

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{"dev"});

        SecuritySecretsValidator validator = new SecuritySecretsValidator(props, env);

        // Should not throw — dev profile is exempt
        validator.validateSecrets();
    }

    @Test
    void shouldPassWhenAllSecretsAreSet() {
        ReclaimProperties props = new ReclaimProperties();
        props.getSecurity().setJwtSecret("my-jwt-secret");
        props.getSecurity().setRefreshSecret("my-refresh-secret");
        props.getSecurity().setEncryptionKey("my-encryption-key");

        Environment env = mock(Environment.class);
        when(env.getActiveProfiles()).thenReturn(new String[]{});

        SecuritySecretsValidator validator = new SecuritySecretsValidator(props, env);

        // Should not throw
        validator.validateSecrets();
    }
}
