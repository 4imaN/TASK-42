package com.reclaim.portal.auth.service;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.common.config.ReclaimProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class JwtService {

    private final ReclaimProperties reclaimProperties;

    public JwtService(ReclaimProperties reclaimProperties) {
        this.reclaimProperties = reclaimProperties;
    }

    public String generateAccessToken(User user) {
        ReclaimProperties.Security security = reclaimProperties.getSecurity();
        SecretKey key = Keys.hmacShaKeyFor(security.getJwtSecret().getBytes(StandardCharsets.UTF_8));

        List<String> roleNames = user.getRoles().stream()
            .map(role -> role.getName())
            .collect(Collectors.toList());

        Instant now = Instant.now();
        Instant expiry = now.plus(security.getAccessTokenMinutes(), ChronoUnit.MINUTES);

        return Jwts.builder()
            .subject(user.getUsername())
            .claim("roles", roleNames)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(key)
            .compact();
    }

    public String generateRefreshToken(User user) {
        ReclaimProperties.Security security = reclaimProperties.getSecurity();
        SecretKey key = Keys.hmacShaKeyFor(security.getRefreshSecret().getBytes(StandardCharsets.UTF_8));

        Instant now = Instant.now();
        Instant expiry = now.plus(security.getRefreshTokenDays(), ChronoUnit.DAYS);

        return Jwts.builder()
            .subject(user.getUsername())
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .id(UUID.randomUUID().toString())
            .signWith(key)
            .compact();
    }

    public String extractUsername(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
            reclaimProperties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public String extractUsernameFromRefreshToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(
            reclaimProperties.getSecurity().getRefreshSecret().getBytes(StandardCharsets.UTF_8));
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                reclaimProperties.getSecurity().getJwtSecret().getBytes(StandardCharsets.UTF_8));
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(
                reclaimProperties.getSecurity().getRefreshSecret().getBytes(StandardCharsets.UTF_8));
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
