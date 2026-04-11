package com.reclaim.portal.common.config;

import com.reclaim.portal.auth.filter.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // CSRF: The access token is delivered as an HttpOnly cookie for SSR page
        // compatibility, so mutable endpoints are cookie-authenticated. CSRF protection
        // is enabled using a cookie-based token repository. The XSRF-TOKEN cookie is
        // JS-readable so apiFetch can include it in the X-XSRF-TOKEN header.
        // Auth endpoints (login/refresh/logout) are excluded because they use a separate
        // refresh cookie with SameSite=Strict + Origin validation.
        // Use plain CsrfTokenRequestAttributeHandler (not the default XOR variant)
        // so the raw token in the XSRF-TOKEN cookie matches what JS sends in the
        // X-XSRF-TOKEN header. The XOR handler would encode the expected value,
        // causing a mismatch with the raw cookie value → 403.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(org.springframework.security.web.csrf.CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
                .ignoringRequestMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout"
                )
            )
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    // API calls get JSON 401; browser navigations redirect to login
                    String accept = request.getHeader("Accept");
                    boolean isApi = request.getRequestURI().startsWith("/api/")
                            || (accept != null && accept.contains("application/json"));
                    if (isApi) {
                        response.setContentType("application/json");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                    } else {
                        response.sendRedirect("/login");
                    }
                })
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/login",
                    "/auth/change-password",
                    "/css/**",
                    "/js/**",
                    "/images/**"
                ).permitAll()
                .requestMatchers("/api/admin/**", "/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/reviewer/**", "/reviewer/**")
                    .hasAnyRole("REVIEWER", "ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Force the CSRF token to load on every response so the XSRF-TOKEN
            // cookie is always present for subsequent JS fetch calls.
            .addFilterAfter(new CsrfCookieForceFilter(), CsrfFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Eagerly loads the {@link CsrfToken} on every request so that
     * {@link org.springframework.security.web.csrf.CookieCsrfTokenRepository}
     * writes the {@code XSRF-TOKEN} cookie in the response. Without this,
     * Spring Security 6.x defers token generation and the cookie may be
     * absent on the first page load, causing 403 on the next JS fetch.
     */
    private static class CsrfCookieForceFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken(); // triggers cookie write
            }
            filterChain.doFilter(request, response);
        }
    }
}
