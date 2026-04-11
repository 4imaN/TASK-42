package com.reclaim.portal.storage.controller;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.appeals.repository.AppealRepository;
import com.reclaim.portal.appeals.repository.EvidenceFileRepository;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.SignatureArtifact;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.contracts.repository.SignatureArtifactRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.entity.ReviewImage;
import com.reclaim.portal.reviews.repository.ReviewImageRepository;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import com.reclaim.portal.storage.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Serves stored files (signatures, evidence images) via a controlled endpoint.
 * Delegates all path validation and traversal protection to {@link StorageService}.
 * Requires authentication — inherits the global security filter chain rules.
 */
@RestController
@RequestMapping("/storage")
public class StorageFileController {

    private static final Logger log = LoggerFactory.getLogger(StorageFileController.class);

    private static final Map<String, MediaType> EXTENSION_MEDIA_TYPE = Map.of(
            "png", MediaType.IMAGE_PNG,
            "jpg", MediaType.IMAGE_JPEG,
            "jpeg", MediaType.IMAGE_JPEG
    );

    private final StorageService storageService;
    private final EvidenceFileRepository evidenceFileRepository;
    private final SignatureArtifactRepository signatureArtifactRepository;
    private final ContractInstanceRepository contractInstanceRepository;
    private final AppealRepository appealRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public StorageFileController(StorageService storageService,
                                 EvidenceFileRepository evidenceFileRepository,
                                 SignatureArtifactRepository signatureArtifactRepository,
                                 ContractInstanceRepository contractInstanceRepository,
                                 AppealRepository appealRepository,
                                 ReviewImageRepository reviewImageRepository,
                                 ReviewRepository reviewRepository,
                                 OrderRepository orderRepository,
                                 UserRepository userRepository) {
        this.storageService = storageService;
        this.evidenceFileRepository = evidenceFileRepository;
        this.signatureArtifactRepository = signatureArtifactRepository;
        this.contractInstanceRepository = contractInstanceRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.reviewRepository = reviewRepository;
        this.orderRepository = orderRepository;
        this.appealRepository = appealRepository;
        this.userRepository = userRepository;
    }

    /**
     * Serves a stored file by its relative path. The path is extracted from
     * everything after {@code /storage/}, e.g. {@code /storage/signatures/abc.png}
     * resolves to relative path {@code signatures/abc.png}.
     *
     * <p>Access control: authentication is required (enforced by the global security
     * filter chain). File names are UUID-based, providing enumeration protection.
     * Ownership is verified by looking up the path in the database and checking
     * that the authenticated user is the file's owner, is the assigned reviewer,
     * or is an admin.
     */
    @GetMapping("/**")
    public ResponseEntity<byte[]> serveFile(HttpServletRequest request, Authentication auth) {
        // Require authentication — the filter chain enforces this, but we verify
        // explicitly here as an additional defence-in-depth check.
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Extract the relative path after "/storage/"
        String fullPath = request.getRequestURI();
        String contextPath = request.getContextPath();
        String relativePath = fullPath.substring(contextPath.length() + "/storage/".length());

        if (relativePath.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // Ownership check — admins can access all files; reviewers and users only their own
        // or assigned entities.
        if (!hasFileAccess(relativePath, auth)) {
            log.warn("Storage access denied: path='{}' user='{}'", relativePath, auth.getName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.debug("Storage file access: path='{}' by user='{}'", relativePath, auth.getName());

        try {
            byte[] data = storageService.retrieve(relativePath);

            // Verify file integrity against stored checksum
            String storedChecksum = resolveStoredChecksum(relativePath);
            if (!verifyIntegrity(data, storedChecksum)) {
                log.warn("File integrity check failed: path='{}' user='{}'", relativePath, auth.getName());
                throw new com.reclaim.portal.common.exception.BusinessRuleException(
                        "File integrity check failed for: " + relativePath);
            }

            MediaType contentType = resolveContentType(relativePath);

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .contentLength(data.length)
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                    .body(data);
        } catch (com.reclaim.portal.common.exception.EntityNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (com.reclaim.portal.common.exception.BusinessRuleException e) {
            log.warn("Blocked storage file request: path='{}' user='{}' reason='{}'",
                    relativePath, auth.getName(), e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    /**
     * Checks whether the authenticated principal may access the given relative storage path.
     * Admins are allowed unrestricted access (for dispute resolution and audit).
     * Reviewers may access files on orders/contracts/appeals they are assigned to.
     * Regular users are allowed only files that belong to them according to the database records.
     */
    private boolean hasFileAccess(String relativePath, Authentication auth) {
        // Admins can access all stored files (for dispute resolution and audit)
        if (isAdmin(auth)) return true;

        Long actorId = resolveUserId(auth);
        if (actorId == null) return false;

        boolean staff = isStaff(auth);

        if (relativePath.startsWith("signatures/")) {
            // Signatures contain PII (handwriting). Only the contract owner may view.
            // Reviewers see masked PII by default — admin reveal (via admin panel) is required.
            List<SignatureArtifact> artifacts = signatureArtifactRepository.findByFilePath(relativePath);
            for (SignatureArtifact artifact : artifacts) {
                ContractInstance contract = contractInstanceRepository
                        .findById(artifact.getContractId()).orElse(null);
                if (contract != null && actorId.equals(contract.getUserId())) return true;
            }
            return false;
        }

        if (relativePath.startsWith("evidence/")) {
            // Evidence: accessible to the uploader, the contract/appeal owner.
            // Reviewers do NOT get automatic access — consistent with PII masking model.
            List<EvidenceFile> files = evidenceFileRepository.findByFilePath(relativePath);
            for (EvidenceFile f : files) {
                if (actorId.equals(f.getUploadedBy())) return true;
                if ("CONTRACT".equals(f.getEntityType())) {
                    ContractInstance contract = contractInstanceRepository.findById(f.getEntityId()).orElse(null);
                    if (contract != null && actorId.equals(contract.getUserId())) return true;
                }
                if ("APPEAL".equals(f.getEntityType())) {
                    Appeal appeal = appealRepository.findById(f.getEntityId()).orElse(null);
                    if (appeal != null && actorId.equals(appeal.getAppellantId())) return true;
                }
            }
            return false;
        }

        if (relativePath.startsWith("reviews/")) {
            // Review images: accessible to the review author or the order owner.
            // Reviewers assigned to the order may also access.
            List<ReviewImage> images = reviewImageRepository.findByFilePath(relativePath);
            for (ReviewImage img : images) {
                Review review = reviewRepository.findById(img.getReviewId()).orElse(null);
                if (review != null) {
                    // Review author can see their own images
                    if (actorId.equals(review.getReviewerUserId())) return true;
                    // Order owner can see reviews for their orders
                    Order order = orderRepository.findById(review.getOrderId()).orElse(null);
                    if (order != null && actorId.equals(order.getUserId())) return true;
                    if (staff && order != null && actorId.equals(order.getReviewerId())) return true;
                }
            }
            return false;
        }

        // Unknown subdirectory: deny by default
        return false;
    }

    /**
     * Looks up the stored SHA-256 checksum for a file by its relative path.
     * Checks evidence files, signature artifacts, and review images.
     */
    private String resolveStoredChecksum(String relativePath) {
        List<EvidenceFile> evidenceFiles = evidenceFileRepository.findByFilePath(relativePath);
        if (!evidenceFiles.isEmpty()) {
            return evidenceFiles.get(0).getChecksum();
        }
        List<SignatureArtifact> sigArtifacts = signatureArtifactRepository.findByFilePath(relativePath);
        if (!sigArtifacts.isEmpty()) {
            return sigArtifacts.get(0).getChecksum();
        }
        List<ReviewImage> reviewImages = reviewImageRepository.findByFilePath(relativePath);
        if (!reviewImages.isEmpty()) {
            return reviewImages.get(0).getChecksum();
        }
        return null;
    }

    /**
     * Computes a SHA-256 checksum from the given bytes and compares against the stored value.
     * Returns true when storedChecksum is null (no checksum to verify against) or when checksums match.
     */
    private boolean verifyIntegrity(byte[] data, String storedChecksum) {
        if (storedChecksum == null) return true;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String computed = java.util.HexFormat.of().formatHex(digest.digest(data));
            return storedChecksum.equals(computed);
        } catch (java.security.NoSuchAlgorithmException e) {
            return true; // SHA-256 is always available on any compliant JVM
        }
    }

    private Long resolveUserId(Authentication auth) {
        if (auth == null) return null;
        String username = ((UserDetails) auth.getPrincipal()).getUsername();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private boolean isAdmin(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }

    private boolean isStaff(Authentication auth) {
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_REVIEWER") || a.equals("ROLE_ADMIN"));
    }

    private MediaType resolveContentType(String filePath) {
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filePath.length() - 1) {
            String ext = filePath.substring(dotIndex + 1).toLowerCase();
            MediaType mediaType = EXTENSION_MEDIA_TYPE.get(ext);
            if (mediaType != null) {
                return mediaType;
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
