package com.reclaim.portal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring Boot context smoke test.
 * Passes if and only if the full application context loads without errors.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReclaimPortalApplicationTest {

    @Test
    void shouldLoadSpringContext() {
        // Empty body: if the @SpringBootTest context fails to start, this test fails.
    }
}
