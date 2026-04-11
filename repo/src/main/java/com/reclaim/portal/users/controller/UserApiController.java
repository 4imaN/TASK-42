package com.reclaim.portal.users.controller;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.users.dto.MaskedUserProfileDto;
import com.reclaim.portal.users.dto.UserProfileDto;
import com.reclaim.portal.users.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserApiController {

    private final UserService userService;
    private final UserRepository userRepository;

    public UserApiController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/users/{id}/profile
     * Admins and the user themselves receive the full profile.
     * Reviewers receive a masked profile.
     */
    @GetMapping("/{id}/profile")
    public ResponseEntity<?> getProfile(@PathVariable Long id, Authentication authentication) {
        boolean isAdmin = hasRole(authentication, "ROLE_ADMIN");
        boolean isReviewer = hasRole(authentication, "ROLE_REVIEWER");
        Long requestingUserId = resolveUserId(authentication);
        boolean isSelf = requestingUserId != null && requestingUserId.equals(id);

        if (isSelf) {
            UserProfileDto dto = userService.getUserProfile(id);
            return ResponseEntity.ok(dto);
        } else if (isAdmin || isReviewer) {
            // Admins and reviewers see masked profiles by default.
            // Full PII requires the explicit POST /reveal endpoint with audit logging.
            MaskedUserProfileDto dto = userService.getMaskedUserProfile(id);
            return ResponseEntity.ok(dto);
        } else {
            throw new com.reclaim.portal.common.exception.BusinessRuleException("Access denied to user profile");
        }
    }

    /**
     * POST /api/users/{id}/reveal
     * Admin only – reveals full PII and logs the access.
     * The endpoint is also accessible via /api/admin/users/{id}/reveal from AdminApiController.
     */
    @PostMapping("/{id}/reveal")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileDto> revealPii(@PathVariable Long id,
                                                    @RequestParam String reason,
                                                    Authentication authentication) {
        Long adminUserId = resolveUserId(authentication);
        UserProfileDto dto = userService.revealPii(adminUserId, id, reason);
        return ResponseEntity.ok(dto);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean hasRole(Authentication auth, String role) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }

    private Long resolveUserId(Authentication auth) {
        if (auth == null) return null;
        String username = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + username));
    }
}
