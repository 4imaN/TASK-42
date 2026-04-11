package com.reclaim.portal.auth.controller;

import com.reclaim.portal.auth.dto.AuthResponse;
import com.reclaim.portal.auth.dto.ChangePasswordRequest;
import com.reclaim.portal.auth.dto.LoginRequest;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.auth.service.AuthService;
import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private static final String REFRESH_COOKIE_NAME = "refreshToken";
    private static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 days in seconds

    private final AuthService authService;
    private final UserRepository userRepository;
    private final ReclaimProperties reclaimProperties;

    public AuthApiController(AuthService authService,
                             UserRepository userRepository,
                             ReclaimProperties reclaimProperties) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.reclaimProperties = reclaimProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        String ipAddress = resolveIpAddress(httpRequest);
        AuthResponse authResponse = authService.login(
            request.getUsername(), request.getPassword(), ipAddress);

        setRefreshCookie(httpResponse, authResponse.getRefreshToken());
        setAccessTokenCookie(httpResponse, authResponse.getAccessToken());

        AuthResponse responseBody = new AuthResponse(
            authResponse.getAccessToken(),
            null,
            authResponse.isForcePasswordReset()
        );
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest httpRequest,
                                                HttpServletResponse httpResponse) {
        validateOrigin(httpRequest);

        String refreshTokenStr = extractRefreshCookie(httpRequest);
        if (refreshTokenStr == null) {
            return ResponseEntity.badRequest().build();
        }

        AuthResponse authResponse = authService.refresh(refreshTokenStr);
        setRefreshCookie(httpResponse, authResponse.getRefreshToken());
        setAccessTokenCookie(httpResponse, authResponse.getAccessToken());

        AuthResponse responseBody = new AuthResponse(
            authResponse.getAccessToken(),
            null,
            authResponse.isForcePasswordReset()
        );
        return ResponseEntity.ok(responseBody);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest,
                                       HttpServletResponse httpResponse) {
        validateOrigin(httpRequest);

        String refreshTokenStr = extractRefreshCookie(httpRequest);
        if (refreshTokenStr != null) {
            authService.logout(refreshTokenStr);
        }
        clearRefreshCookie(httpResponse);
        clearAccessTokenCookie(httpResponse);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(
                "User not found: " + userDetails.getUsername()));

        authService.changePassword(user.getId(), request.getOldPassword(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(reclaimProperties.getSecurity().isCookieSecure())
            .path("/api/auth")
            .maxAge(REFRESH_COOKIE_MAX_AGE)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(reclaimProperties.getSecurity().isCookieSecure())
            .path("/api/auth")
            .maxAge(0)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", accessToken)
            .httpOnly(true)
            .secure(reclaimProperties.getSecurity().isCookieSecure())
            .path("/")
            .maxAge(reclaimProperties.getSecurity().getAccessTokenMinutes() * 60)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("accessToken", "")
            .httpOnly(true)
            .secure(reclaimProperties.getSecurity().isCookieSecure())
            .path("/")
            .maxAge(0)
            .sameSite("Strict")
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String resolveIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Validates the Origin header on cookie-bearing endpoints (refresh, logout) as a
     * defence-in-depth CSRF mitigation alongside the SameSite=Strict cookie attribute.
     *
     * If no Origin header is present the request is allowed — same-origin requests from the
     * same page do not always include Origin. Only explicit cross-origin requests that set a
     * different Origin are rejected.
     */
    private void validateOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isEmpty()) {
            String expectedOrigin = request.getScheme() + "://" + request.getServerName();
            int port = request.getServerPort();
            if (port != 80 && port != 443) {
                expectedOrigin += ":" + port;
            }
            if (!origin.equals(expectedOrigin)) {
                throw new com.reclaim.portal.common.exception.BusinessRuleException(
                    "Cross-origin request rejected");
            }
        }
    }
}
