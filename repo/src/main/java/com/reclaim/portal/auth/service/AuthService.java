package com.reclaim.portal.auth.service;

import com.reclaim.portal.auth.dto.AuthResponse;
import com.reclaim.portal.auth.entity.LoginAttempt;
import com.reclaim.portal.auth.entity.RefreshToken;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.LoginAttemptRepository;
import com.reclaim.portal.auth.repository.RefreshTokenRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@Transactional
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ReclaimProperties reclaimProperties;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       LoginAttemptRepository loginAttemptRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       ReclaimProperties reclaimProperties) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.reclaimProperties = reclaimProperties;
    }

    public AuthResponse login(String username, String password, String ipAddress) {
        ReclaimProperties.Security security = reclaimProperties.getSecurity();
        LocalDateTime now = LocalDateTime.now();

        User user = userRepository.findByUsername(username)
            .orElse(null);

        // Check disabled state before verifying credentials
        if (user != null && !user.isEnabled()) {
            log.warn("Login rejected: account disabled for user={} from ip={}", username, ipAddress);
            recordFailedAttempt(username, ipAddress, "Account is disabled");
            throw new BusinessRuleException("Account is disabled");
        }

        // Check lock state before verifying credentials
        if (user != null && user.isLocked() && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(now)) {
            log.warn("Login rejected: account locked for user={} from ip={}", username, ipAddress);
            recordFailedAttempt(username, ipAddress, "Account is locked");
            throw new BusinessRuleException("Account is locked");
        }

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            if (user != null) {
                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);
                if (attempts >= security.getMaxFailedAttempts()) {
                    user.setLocked(true);
                    user.setLockedUntil(now.plusMinutes(security.getLockoutMinutes()));
                    log.warn("Account locked after {} failed attempts: user={}", attempts, username);
                }
                user.setUpdatedAt(now);
                userRepository.save(user);
            }
            log.info("Login failed for user={} from ip={}", username, ipAddress);
            recordFailedAttempt(username, ipAddress, "Invalid credentials");
            throw new BusinessRuleException("Invalid username or password");
        }

        // Successful authentication
        log.info("Login successful for user={}", username);
        user.setFailedAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        user.setUpdatedAt(now);
        userRepository.save(user);

        LoginAttempt successAttempt = new LoginAttempt();
        successAttempt.setUsername(username);
        successAttempt.setIpAddress(ipAddress);
        successAttempt.setSuccess(true);
        successAttempt.setAttemptedAt(now);
        loginAttemptRepository.save(successAttempt);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = jwtService.generateRefreshToken(user);
        String tokenHash = hashToken(refreshTokenStr);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(now.plusDays(security.getRefreshTokenDays()));
        refreshToken.setRevoked(false);
        refreshToken.setCreatedAt(now);
        refreshTokenRepository.save(refreshToken);

        return new AuthResponse(accessToken, refreshTokenStr, user.isForcePasswordReset());
    }

    public AuthResponse refresh(String refreshTokenStr) {
        if (!jwtService.isRefreshTokenValid(refreshTokenStr)) {
            log.warn("Refresh token validation failed");
            throw new BusinessRuleException("Invalid refresh token");
        }

        String tokenHash = hashToken(refreshTokenStr);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new BusinessRuleException("Refresh token not found"));

        if (storedToken.isRevoked()) {
            throw new BusinessRuleException("Refresh token has been revoked");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("Refresh token has expired");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        String username = jwtService.extractUsernameFromRefreshToken(refreshTokenStr);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));

        if (!user.isEnabled()) {
            log.warn("Token refresh rejected: account disabled for user={}", username);
            throw new BusinessRuleException("Account is disabled");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.isLocked() && user.getLockedUntil() != null
                && user.getLockedUntil().isAfter(now)) {
            log.warn("Token refresh rejected: account locked for user={}", username);
            throw new BusinessRuleException("Account is locked");
        }

        ReclaimProperties.Security security = reclaimProperties.getSecurity();

        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshTokenStr = jwtService.generateRefreshToken(user);
        String newTokenHash = hashToken(newRefreshTokenStr);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setUserId(user.getId());
        newRefreshToken.setTokenHash(newTokenHash);
        newRefreshToken.setExpiresAt(now.plusDays(security.getRefreshTokenDays()));
        newRefreshToken.setRevoked(false);
        newRefreshToken.setCreatedAt(now);
        refreshTokenRepository.save(newRefreshToken);

        return new AuthResponse(newAccessToken, newRefreshTokenStr, user.isForcePasswordReset());
    }

    public void logout(String refreshTokenStr) {
        String tokenHash = hashToken(refreshTokenStr);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    public void changePassword(Long userId, String oldPass, String newPass) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User", userId));

        if (!passwordEncoder.matches(oldPass, user.getPasswordHash())) {
            log.warn("Password change failed: incorrect current password for userId={}", userId);
            throw new BusinessRuleException("Current password is incorrect");
        }

        validatePasswordStrength(newPass);

        user.setPasswordHash(passwordEncoder.encode(newPass));
        user.setForcePasswordReset(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("Password changed successfully for userId={}", userId);
    }

    private void validatePasswordStrength(String password) {
        if (password == null || password.length() < 12) {
            throw new BusinessRuleException(
                "Password must be at least 12 characters long");
        }
        if (!password.chars().anyMatch(Character::isUpperCase)) {
            throw new BusinessRuleException(
                "Password must contain at least one uppercase letter");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)) {
            throw new BusinessRuleException(
                "Password must contain at least one lowercase letter");
        }
        if (!password.chars().anyMatch(Character::isDigit)) {
            throw new BusinessRuleException(
                "Password must contain at least one digit");
        }
        if (!password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            throw new BusinessRuleException(
                "Password must contain at least one special character");
        }
    }

    private void recordFailedAttempt(String username, String ipAddress, String reason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsername(username);
        attempt.setIpAddress(ipAddress);
        attempt.setSuccess(false);
        attempt.setFailureReason(reason);
        attempt.setAttemptedAt(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
