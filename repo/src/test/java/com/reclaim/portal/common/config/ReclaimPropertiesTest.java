package com.reclaim.portal.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link ReclaimProperties} binds correctly from the test profile configuration.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReclaimPropertiesTest {

    @Autowired
    private ReclaimProperties reclaimProperties;

    // -------------------------------------------------------------------------
    // Security section
    // -------------------------------------------------------------------------

    @Test
    void shouldBindSecurityDefaults() {
        ReclaimProperties.Security security = reclaimProperties.getSecurity();

        assertThat(security).isNotNull();
        assertThat(security.getAccessTokenMinutes()).isGreaterThan(0);
        assertThat(security.getRefreshTokenDays()).isGreaterThan(0);
        assertThat(security.getMaxFailedAttempts()).isGreaterThan(0);
        assertThat(security.getLockoutMinutes()).isGreaterThan(0);
        assertThat(security.getJwtSecret()).isNotBlank();
    }

    // -------------------------------------------------------------------------
    // Storage section
    // -------------------------------------------------------------------------

    @Test
    void shouldBindStorageDefaults() {
        ReclaimProperties.Storage storage = reclaimProperties.getStorage();

        assertThat(storage.getRootPath()).isNotBlank();
        assertThat(storage.getMaxFileSize()).isGreaterThan(0);
        assertThat(storage.getAllowedExtensions()).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Appointments section
    // -------------------------------------------------------------------------

    @Test
    void shouldBindAppointmentDefaults() {
        ReclaimProperties.Appointments appointments = reclaimProperties.getAppointments();

        assertThat(appointments.getBusinessStartHour()).isGreaterThanOrEqualTo(0);
        assertThat(appointments.getMinAdvanceHours()).isGreaterThan(0);
        assertThat(appointments.getMaxAdvanceDays()).isGreaterThan(0);
    }

    // -------------------------------------------------------------------------
    // Contracts section
    // -------------------------------------------------------------------------

    @Test
    void shouldBindContractDefaults() {
        ReclaimProperties.Contracts contracts = reclaimProperties.getContracts();

        assertThat(contracts.getExpiringSoonDays()).isGreaterThan(0);
    }
}
