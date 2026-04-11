package com.reclaim.portal.security;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.AuthService;
import com.reclaim.portal.auth.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtFilterIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

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
        testUser.setUsername("jwtfiltertest_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        testUser.setEmail("jwttest@example.com");
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
    void shouldAllowWithValidToken() throws Exception {
        String token = jwtService.generateAccessToken(testUser);

        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        // Invalid JWT — filter rejects and request becomes anonymous → 401
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer invalid.jwt.token"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowPublicEndpoints() throws Exception {
        // /api/auth/login is public — the endpoint is accessible without authentication.
        // GET is not a mapped HTTP method for this endpoint, so the server may return
        // 405 (Method Not Allowed) or 500, but crucially NOT 401 or 403.
        int status = mockMvc.perform(get("/api/auth/login"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldRejectRequestWithNoToken() throws Exception {
        // No token → anonymous request to protected endpoint → 401
        mockMvc.perform(get("/api/orders/my"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPassThroughWhenAuthorizationHeaderLacksBearer() throws Exception {
        // Header present but not "Bearer ..." prefix — filter skips token processing
        // Anonymous request → 401
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Basic somebase64=="))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldPassThroughWhenAuthorizationHeaderIsEmpty() throws Exception {
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", ""))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldSkipFilterForCssPath() throws Exception {
        // /css/ paths are in shouldNotFilter — no JWT needed; Spring may return 404 but not 401/403
        int status = mockMvc.perform(get("/css/styles.css"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldSkipFilterForJsPath() throws Exception {
        int status = mockMvc.perform(get("/js/app.js"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldSkipFilterForImagesPath() throws Exception {
        int status = mockMvc.perform(get("/images/logo.png"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldSkipFilterForLoginPath() throws Exception {
        int status = mockMvc.perform(get("/login"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldSkipFilterForAuthApiPath() throws Exception {
        // /api/auth/ paths bypass the JWT filter (handled in shouldNotFilter)
        int status = mockMvc.perform(get("/api/auth/login"))
               .andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void shouldAuthenticateAndSetSecurityContextWithValidToken() throws Exception {
        String token = jwtService.generateAccessToken(testUser);

        // With a valid token, the filter sets the security context and the endpoint returns 200
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
    }

    @Test
    void shouldRejectValidTokenWhenAccountDisabled() throws Exception {
        // Generate token while account is enabled
        String token = jwtService.generateAccessToken(testUser);

        // Disable the account after token issuance
        testUser.setEnabled(false);
        testUser.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(testUser);

        // Previously valid token should now result in 401 (filter skips auth setup)
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectValidTokenWhenAccountLocked() throws Exception {
        // Generate token while account is unlocked
        String token = jwtService.generateAccessToken(testUser);

        // Lock the account after token issuance
        testUser.setLocked(true);
        testUser.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        testUser.setUpdatedAt(java.time.LocalDateTime.now());
        userRepository.save(testUser);

        // Previously valid token should now result in 401 (filter skips auth setup)
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isUnauthorized());
    }
}
