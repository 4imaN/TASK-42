package com.reclaim.portal.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

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
        testUser.setUsername("api_test_user_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        testUser.setEmail("apitest@example.com");
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
    void shouldLoginAndReceiveToken() throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "TestPassword1!"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.accessToken").exists())
               .andExpect(jsonPath("$.forcePasswordReset").value(false))
               .andExpect(cookie().exists("refreshToken"))
               .andExpect(cookie().httpOnly("refreshToken", true));
    }

    @Test
    void shouldLogin() throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "TestPassword1!"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.accessToken").isNotEmpty())
               .andExpect(cookie().exists("refreshToken"));
    }

    @Test
    void shouldRefreshToken() throws Exception {
        // First login to get refresh token cookie and initial access token
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "TestPassword1!"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().isOk())
               .andReturn();

        String accessToken1 = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // Extract refresh token from cookie
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();

        // Refresh tokens now include a UUID jti, so no sleep needed for uniqueness
        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.accessToken").isNotEmpty())
               .andReturn();

        String accessToken2 = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        // New access token must be different from the old one
        assertThat(accessToken2).isNotEqualTo(accessToken1);
    }

    @Test
    void shouldRefresh() throws Exception {
        // First login to get refresh token cookie
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "TestPassword1!"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().isOk())
               .andReturn();

        // Extract refresh token from cookie
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertThat(refreshCookie).isNotNull();

        // Use refresh token to get new access token
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void shouldLogout() throws Exception {
        // Login first
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "TestPassword1!"
        );

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().isOk())
               .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                .cookie(refreshCookie != null ? refreshCookie : new Cookie("refreshToken", "")))
               .andExpect(status().isOk());
    }

    @Test
    void shouldRejectBadCredentials() throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", testUser.getUsername(),
            "password", "WrongPassword123!"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRejectLoginForNonExistentUser() throws Exception {
        Map<String, String> loginRequest = Map.of(
            "username", "nonexistent_user_xyz",
            "password", "SomePassword1!"
        );

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
               .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {
        String accessToken = jwtService.generateAccessToken(testUser);

        Map<String, String> changeRequest = Map.of(
            "oldPassword", "TestPassword1!",
            "newPassword", "NewSecurePass2@"
        );

        mockMvc.perform(post("/api/auth/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest))
                .header("Authorization", "Bearer " + accessToken))
               .andExpect(status().isOk());
    }

    @Test
    void shouldRejectChangePasswordWithWrongOldPassword() throws Exception {
        String accessToken = jwtService.generateAccessToken(testUser);

        Map<String, String> changeRequest = Map.of(
            "oldPassword", "WrongOldPassword1!",
            "newPassword", "NewSecurePass2@"
        );

        // Wrong old password → BusinessRuleException("Current password is incorrect") → 409
        mockMvc.perform(post("/api/auth/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest))
                .header("Authorization", "Bearer " + accessToken))
               .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectChangePasswordWithWeakNewPassword() throws Exception {
        String accessToken = jwtService.generateAccessToken(testUser);

        Map<String, String> changeRequest = Map.of(
            "oldPassword", "TestPassword1!",
            "newPassword", "weak"
        );

        // Weak new password → BusinessRuleException("Password must be...") → 409
        mockMvc.perform(post("/api/auth/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest))
                .header("Authorization", "Bearer " + accessToken))
               .andExpect(status().isConflict());
    }

    @Test
    void shouldRejectChangePasswordWithoutAuthentication() throws Exception {
        Map<String, String> changeRequest = Map.of(
            "oldPassword", "TestPassword1!",
            "newPassword", "NewSecurePass2@"
        );

        int statusCode = mockMvc.perform(post("/api/auth/change-password")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changeRequest)))
               .andReturn().getResponse().getStatus();
        // Without a valid JWT, request is rejected by the security filter → 401
        assertThat(statusCode).isIn(401, 403);
    }

    @Test
    void shouldRejectRefreshWithBadOrigin() throws Exception {
        // Cross-origin request to refresh endpoint should be rejected
        mockMvc.perform(post("/api/auth/refresh")
                .header("Origin", "https://evil.example.com")
                .cookie(new Cookie("refreshToken", "some-token")))
               .andExpect(status().is(409));
    }

    @Test
    void shouldRejectLogoutWithBadOrigin() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header("Origin", "https://evil.example.com")
                .cookie(new Cookie("refreshToken", "some-token")))
               .andExpect(status().is(409));
    }

    @Test
    void shouldReturnBadRequestForRefreshWithoutCookie() throws Exception {
        mockMvc.perform(post("/api/auth/refresh"))
               .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLogoutWithoutCookieGracefully() throws Exception {
        // Logout with no cookie should succeed (no-op)
        mockMvc.perform(post("/api/auth/logout"))
               .andExpect(status().isOk());
    }
}
