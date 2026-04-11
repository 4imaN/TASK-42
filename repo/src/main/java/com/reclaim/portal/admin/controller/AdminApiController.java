package com.reclaim.portal.admin.controller;

import com.reclaim.portal.admin.service.AdminService;
import com.reclaim.portal.auth.entity.AdminAccessLog;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.search.entity.RankingStrategyVersion;
import com.reclaim.portal.users.dto.UserProfileDto;
import com.reclaim.portal.users.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminApiController {

    private final AdminService adminService;
    private final UserService userService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Request body records
    // -------------------------------------------------------------------------

    record CreateStrategyRequest(
            String versionLabel,
            BigDecimal creditScoreWeight,
            BigDecimal positiveRateWeight,
            BigDecimal reviewQualityWeight,
            BigDecimal minCreditScoreThreshold,
            BigDecimal minPositiveRateThreshold
    ) {}

    public AdminApiController(AdminService adminService, UserService userService,
                              UserRepository userRepository) {
        this.adminService = adminService;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/admin/strategies — create a new ranking strategy.
     */
    @PostMapping("/strategies")
    public ResponseEntity<RankingStrategyVersion> createStrategy(
            @RequestBody CreateStrategyRequest req, Authentication auth) {
        Long adminId = resolveUserId(auth);
        return ResponseEntity.ok(adminService.createRankingStrategy(
                req.versionLabel(),
                req.creditScoreWeight(),
                req.positiveRateWeight(),
                req.reviewQualityWeight(),
                req.minCreditScoreThreshold(),
                req.minPositiveRateThreshold(),
                adminId
        ));
    }

    /**
     * PUT /api/admin/strategies/{id}/activate — activate a strategy (deactivates all others).
     */
    @PutMapping("/strategies/{id}/activate")
    public ResponseEntity<RankingStrategyVersion> activateStrategy(
            @PathVariable Long id, Authentication auth) {
        Long adminId = resolveUserId(auth);
        return ResponseEntity.ok(adminService.activateStrategy(id, adminId));
    }

    /**
     * GET /api/admin/strategies — list all strategies.
     */
    @GetMapping("/strategies")
    public ResponseEntity<List<RankingStrategyVersion>> getStrategies() {
        return ResponseEntity.ok(adminService.getStrategies());
    }

    /**
     * GET /api/admin/strategies/active — get the currently active strategy.
     */
    @GetMapping("/strategies/active")
    public ResponseEntity<RankingStrategyVersion> getActiveStrategy() {
        return ResponseEntity.ok(adminService.getActiveStrategy());
    }

    /**
     * GET /api/admin/analytics/search — search analytics (totals, top terms, recent).
     */
    @GetMapping("/analytics/search")
    public ResponseEntity<Map<String, Object>> getSearchAnalytics() {
        return ResponseEntity.ok(adminService.getSearchAnalytics());
    }

    /**
     * GET /api/admin/access-logs — admin access log entries.
     */
    @GetMapping("/access-logs")
    public ResponseEntity<List<AdminAccessLog>> getAccessLogs() {
        return ResponseEntity.ok(adminService.getAccessLogs());
    }

    /**
     * POST /api/admin/users/{id}/reveal — reveals PII for target user, logs the access.
     */
    @PostMapping("/users/{id}/reveal")
    public ResponseEntity<UserProfileDto> revealPii(@PathVariable Long id,
                                                    @RequestParam String reason,
                                                    Authentication auth) {
        Long adminUserId = resolveUserId(auth);
        return ResponseEntity.ok(userService.revealPii(adminUserId, id, reason));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long resolveUserId(Authentication auth) {
        String username = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }
}
