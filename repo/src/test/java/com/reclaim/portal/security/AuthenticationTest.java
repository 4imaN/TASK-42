package com.reclaim.portal.security;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User regularUser;

    @BeforeEach
    void setUp() {
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });

        regularUser = new User();
        regularUser.setUsername("authtest_user_" + System.nanoTime());
        regularUser.setPasswordHash(passwordEncoder.encode("TestPassword1!"));
        regularUser.setEmail("authtest@example.com");
        regularUser.setEnabled(true);
        regularUser.setLocked(false);
        regularUser.setForcePasswordReset(false);
        regularUser.setFailedAttempts(0);
        regularUser.setCreatedAt(LocalDateTime.now());
        regularUser.setUpdatedAt(LocalDateTime.now());
        regularUser.setRoles(new HashSet<>(Set.of(userRole)));
        regularUser = userRepository.save(regularUser);
    }

    @Test
    void shouldReturn401WhenNoToken() throws Exception {
        // Custom AuthenticationEntryPoint returns 401 for unauthenticated requests
        mockMvc.perform(get("/api/orders/my"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WithBadToken() throws Exception {
        // Invalid JWT results in anonymous request → 401
        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer completely.invalid.jwt"))
               .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn403ForWrongRole() throws Exception {
        // Regular user (ROLE_USER) attempting to access admin endpoint
        String token = jwtService.generateAccessToken(regularUser);

        mockMvc.perform(get("/api/admin/strategies")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAuthorizedRequest() throws Exception {
        String token = jwtService.generateAccessToken(regularUser);

        mockMvc.perform(get("/api/orders/my")
                .header("Authorization", "Bearer " + token))
               .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAccessToLoginPath() throws Exception {
        // /login is a public endpoint — accessible without token
        mockMvc.perform(get("/login"))
               .andExpect(status().is2xxSuccessful());
    }
}
