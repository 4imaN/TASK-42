package com.reclaim.portal.contracts.controller;

import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.contracts.entity.*;
import com.reclaim.portal.contracts.service.ContractService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/contracts")
public class ContractApiController {

    private final ContractService contractService;
    private final UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Request body records
    // -------------------------------------------------------------------------

    record CreateTemplateRequest(String name, String description) {}

    record CreateVersionRequest(String content, String changeNotes) {}

    record AddClauseFieldRequest(String fieldName, String fieldType, String fieldLabel,
                                 boolean required, String defaultValue, int displayOrder) {}

    record InitiateContractRequest(Long orderId, Long templateVersionId,
                                   String fieldValues, LocalDate startDate, LocalDate endDate) {}

    record ConfirmContractRequest(Long actorId) {}

    record RenewContractRequest(LocalDate newEndDate) {}

    public ContractApiController(ContractService contractService, UserRepository userRepository) {
        this.contractService = contractService;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // Template management (admin)
    // =========================================================================

    @PostMapping("/templates")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractTemplate> createTemplate(
            @RequestBody CreateTemplateRequest req, Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(contractService.createTemplate(req.name(), req.description(), userId));
    }

    @PostMapping("/templates/{id}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractTemplateVersion> createVersion(
            @PathVariable Long id, @RequestBody CreateVersionRequest req, Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(
                contractService.createTemplateVersion(id, req.content(), req.changeNotes(), userId));
    }

    @PostMapping("/templates/versions/{id}/fields")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractClauseField> addField(
            @PathVariable Long id, @RequestBody AddClauseFieldRequest req) {
        return ResponseEntity.ok(contractService.addClauseField(
                id, req.fieldName(), req.fieldType(), req.fieldLabel(),
                req.required(), req.defaultValue(), req.displayOrder()));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<ContractTemplate>> getActiveTemplates() {
        return ResponseEntity.ok(contractService.getActiveTemplates());
    }

    @GetMapping("/templates/{id}/versions")
    public ResponseEntity<List<ContractTemplateVersion>> getVersions(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getTemplateVersions(id));
    }

    @GetMapping("/templates/versions/{id}/fields")
    public ResponseEntity<List<ContractClauseField>> getFields(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.getClauseFields(id));
    }

    // =========================================================================
    // Contract lifecycle
    // =========================================================================

    @PostMapping
    @PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
    public ResponseEntity<ContractInstance> initiateContract(
            @RequestBody InitiateContractRequest req, Authentication auth) {
        Long reviewerId = resolveUserId(auth);
        return ResponseEntity.ok(contractService.initiateContract(
                req.orderId(), req.templateVersionId(), reviewerId,
                reviewerId, req.fieldValues(), req.startDate(), req.endDate()));
    }

    @PutMapping("/{id}/confirm")
    public ResponseEntity<ContractInstance> confirmContract(
            @PathVariable Long id, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.confirmContract(id, actorId, staff));
    }

    @PutMapping("/{id}/sign")
    public ResponseEntity<ContractInstance> signContract(
            @PathVariable Long id,
            @RequestParam String signatureType,
            @RequestPart("file") MultipartFile file,
            Authentication auth) throws IOException {
        // signerId is the authenticated user; service enforces that signer == contract user
        Long signerId = resolveUserId(auth);
        return ResponseEntity.ok(
                contractService.signContract(id, signerId, signatureType, file.getBytes()));
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractInstance> archiveContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.archiveContract(id));
    }

    @PutMapping("/{id}/terminate")
    @PreAuthorize("hasAnyRole('ADMIN','REVIEWER')")
    public ResponseEntity<ContractInstance> terminateContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.terminateContract(id));
    }

    @PutMapping("/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ContractInstance> voidContract(@PathVariable Long id) {
        return ResponseEntity.ok(contractService.voidContract(id));
    }

    @PutMapping("/{id}/renew")
    public ResponseEntity<ContractInstance> renewContract(
            @PathVariable Long id, @RequestBody RenewContractRequest req, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.renewContract(id, actorId, req.newEndDate(), staff));
    }

    // =========================================================================
    // Queries
    // =========================================================================

    @GetMapping("/my")
    public ResponseEntity<List<ContractInstance>> myContracts(Authentication auth) {
        Long userId = resolveUserId(auth);
        return ResponseEntity.ok(contractService.getUserContracts(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContractInstance> getContract(@PathVariable Long id, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.getContractDetail(id, actorId, staff));
    }

    @GetMapping("/{id}/print")
    public ResponseEntity<ContractInstance> printContract(@PathVariable Long id, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.getPrintableContract(id, actorId, staff));
    }

    @PostMapping("/{id}/evidence")
    public ResponseEntity<EvidenceFile> addEvidence(@PathVariable Long id,
            @RequestPart("file") MultipartFile file, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.addContractEvidence(id, actorId, staff, file));
    }

    @GetMapping("/{id}/evidence")
    public ResponseEntity<List<EvidenceFile>> getEvidence(@PathVariable Long id, Authentication auth) {
        Long actorId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        return ResponseEntity.ok(contractService.getContractEvidence(id, actorId, staff));
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
