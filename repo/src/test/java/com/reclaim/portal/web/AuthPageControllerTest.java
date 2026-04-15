package com.reclaim.portal.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for AuthPageController SSR routes (/login, /auth/change-password).
 * Both endpoints are declared in the permitAll() matcher in SecurityConfig,
 * so they should be accessible without authentication.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthPageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * GET /login without authentication must return 200 and resolve view "auth/login".
     * /login is in the permitAll() list in SecurityConfig.
     */
    @Test
    void shouldReturnLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(view().name("auth/login"));
    }

    /**
     * GET /auth/change-password without authentication must return 200 and resolve view
     * "auth/change-password". /auth/change-password is in the permitAll() list.
     */
    @Test
    void shouldReturnChangePasswordPageWhenUnauthenticated() throws Exception {
        // /auth/change-password is in the permitAll matchers (see SecurityConfig)
        // so unauthenticated requests are allowed → 200
        mockMvc.perform(get("/auth/change-password"))
               .andExpect(status().isOk())
               .andExpect(view().name("auth/change-password"));
    }
}
