package com.reclaim.portal.appeals.controller;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.appeals.service.AppealService;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appeals")
public class AppealApiController {

    private final AppealService appealService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Request body records
    // -------------------------------------------------------------------------

    record CreateAppealRequest(Long orderId, Long contractId, String reason) {}

    record ResolveAppealRequest(String outcome, String reasoning) {}

    public AppealApiController(AppealService appealService, UserRepository userRepository) {
        this.appealService = appealService;
        this.userRepository = userRepository;
    }

    /**
     * POST /api/appeals — create a new appeal.
     */
    @PostMapping
    public ResponseEntity<Appeal> createAppeal(
            @RequestBody CreateAppealRequest req, Authentication auth) {
        Long appellantId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        Appeal appeal = appealService.createAppeal(
                req.orderId(), req.contractId(), appellantId, req.reason(), staff);
        return ResponseEntity.ok(appeal);
    }

    /**
     * POST /api/appeals/{id}/evidence — upload an evidence file.
     */
    @PostMapping("/{id}/evidence")
    public ResponseEntity<EvidenceFile> addEvidence(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            Authentication auth) {
        Long uploadedBy = resolveUserId(auth);
        boolean staff = isStaff(auth);
        EvidenceFile evidence = appealService.addEvidence(id, uploadedBy, file, staff);
        return ResponseEntity.ok(evidence);
    }

    /**
     * PUT /api/appeals/{id}/resolve — resolve an open appeal (admin/reviewer).
     */
    @PutMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<Appeal> resolveAppeal(
            @PathVariable Long id,
            @RequestBody ResolveAppealRequest req,
            Authentication auth) {
        Long decidedBy = resolveUserId(auth);
        Appeal appeal = appealService.resolveAppeal(id, decidedBy, req.outcome(), req.reasoning());
        return ResponseEntity.ok(appeal);
    }

    /**
     * GET /api/appeals/{id} — get appeal details including evidence and outcome.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getAppeal(@PathVariable Long id,
                                                         Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(appealService.getAppealDetails(id, actorId, staff));
    }

    /**
     * GET /api/appeals/my — get all appeals for the current user.
     */
    @GetMapping("/my")
    public ResponseEntity<List<Appeal>> myAppeals(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(appealService.getAppealsForUser(userId));
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

    private boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_REVIEWER") || a.equals("ROLE_ADMIN"));
    }
}
