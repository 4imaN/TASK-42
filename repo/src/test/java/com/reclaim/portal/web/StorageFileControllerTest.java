package com.reclaim.portal.web;

import com.reclaim.portal.auth.entity.Role;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.RoleRepository;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.entity.ReviewImage;
import com.reclaim.portal.reviews.repository.ReviewImageRepository;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import com.reclaim.portal.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for the StorageFileController that serves stored files (signatures, evidence).
 * Verifies that files can be retrieved, content types are correct,
 * and path traversal attempts are blocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class StorageFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ReviewImageRepository reviewImageRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String storedFilePath;
    private Long testReviewId;

    @BeforeEach
    void setUp() {
        // Store a minimal PNG signature file
        byte[] minimalPng = createMinimalPng();
        MockMultipartFile sigFile = new MockMultipartFile(
                "file", "test_signature.png", "image/png", minimalPng);
        var result = storageService.store(sigFile, "signatures");
        storedFilePath = result.filePath(); // e.g. "signatures/<uuid>.png"

        // Create entities needed for review image tests (FK constraints)
        Role userRole = roleRepository.findByName("ROLE_USER").orElseGet(() -> {
            Role r = new Role();
            r.setName("ROLE_USER");
            r.setCreatedAt(LocalDateTime.now());
            return roleRepository.save(r);
        });
        User testUser = new User();
        testUser.setUsername("storage_test_user_" + System.nanoTime());
        testUser.setPasswordHash(passwordEncoder.encode("Pass1!wordXXX"));
        testUser.setEnabled(true);
        testUser.setLocked(false);
        testUser.setForcePasswordReset(false);
        testUser.setFailedAttempts(0);
        testUser.setCreatedAt(LocalDateTime.now());
        testUser.setUpdatedAt(LocalDateTime.now());
        testUser.setRoles(new HashSet<>(Set.of(userRole)));
        testUser = userRepository.save(testUser);

        Order testOrder = new Order();
        testOrder.setUserId(testUser.getId());
        testOrder.setOrderStatus("COMPLETED");
        testOrder.setAppointmentType("PICKUP");
        testOrder.setRescheduleCount(0);
        testOrder.setCurrency("USD");
        testOrder.setTotalPrice(BigDecimal.TEN);
        testOrder.setCreatedAt(LocalDateTime.now());
        testOrder.setUpdatedAt(LocalDateTime.now());
        testOrder = orderRepository.save(testOrder);

        Review testReview = new Review();
        testReview.setOrderId(testOrder.getId());
        testReview.setReviewerUserId(testUser.getId());
        testReview.setRating(4);
        testReview.setReviewText("Good");
        testReview.setCreatedAt(LocalDateTime.now());
        testReview.setUpdatedAt(LocalDateTime.now());
        testReview = reviewRepository.save(testReview);
        testReviewId = testReview.getId();
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"})
    void shouldServeStoredFileWithCorrectContentType() throws Exception {
        // Admins can access any stored file (unrestricted for dispute resolution and audit)
        mockMvc.perform(get("/storage/" + storedFilePath))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.IMAGE_PNG))
               .andExpect(header().exists("Content-Length"))
               .andExpect(header().exists("Cache-Control"));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"})
    void shouldReturn404ForNonexistentFile() throws Exception {
        mockMvc.perform(get("/storage/signatures/nonexistent-file.png"))
               .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldDenyRegularUserAccessToUnownedSignature() throws Exception {
        // Regular user with no linked contract gets 403
        mockMvc.perform(get("/storage/" + storedFilePath))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldBlockPathTraversalAttempts() throws Exception {
        // Spring's URL normalization rejects ".." paths with 400 before reaching the controller
        mockMvc.perform(get("/storage/../etc/passwd"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldBlockPathTraversalInNestedPath() throws Exception {
        // Spring's URL normalization rejects ".." paths with 400 before reaching the controller
        mockMvc.perform(get("/storage/signatures/../../etc/shadow"))
               .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"USER"})
    void shouldBlockEncodedPathTraversalAtService() throws Exception {
        // Even if someone encodes the "..", the StorageService blocks it
        // This tests the controller's handling of the BusinessRuleException from StorageService
        mockMvc.perform(get("/storage/..%2Fetc%2Fpasswd"))
               .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRequireAuthenticationForStorageAccess() throws Exception {
        // No @WithMockUser — unauthenticated browser request redirects to login
        mockMvc.perform(get("/storage/" + storedFilePath))
               .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"})
    void shouldReturnImageDataMatchingOriginalSize() throws Exception {
        byte[] originalPng = createMinimalPng();

        mockMvc.perform(get("/storage/" + storedFilePath))
               .andExpect(status().isOk())
               .andExpect(content().bytes(originalPng));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"})
    void shouldServeReviewImageWhenChecksumMatches() throws Exception {
        // Store a review image file
        byte[] imageData = createMinimalPng();
        MockMultipartFile imgFile = new MockMultipartFile(
                "file", "review_img.png", "image/png", imageData);
        var result = storageService.store(imgFile, "reviews");

        // Create a ReviewImage record with the correct checksum
        String checksum = sha256Hex(imageData);
        ReviewImage ri = new ReviewImage();
        ri.setReviewId(testReviewId);
        ri.setFileName("review_img.png");
        ri.setFilePath(result.filePath());
        ri.setContentType("image/png");
        ri.setFileSize(imageData.length);
        ri.setChecksum(checksum);
        ri.setDisplayOrder(1);
        ri.setUploadedAt(LocalDateTime.now());
        reviewImageRepository.save(ri);

        // Correct checksum — should serve successfully
        mockMvc.perform(get("/storage/" + result.filePath()))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.IMAGE_PNG));
    }

    @Test
    @WithMockUser(username = "testuser", roles = {"ADMIN"})
    void shouldRejectTamperedReviewImage() throws Exception {
        // Store a review image file
        byte[] imageData = createMinimalPng();
        MockMultipartFile imgFile = new MockMultipartFile(
                "file", "tampered_img.png", "image/png", imageData);
        var result = storageService.store(imgFile, "reviews");

        // Create a ReviewImage record with a WRONG checksum (simulating tamper detection)
        ReviewImage ri = new ReviewImage();
        ri.setReviewId(testReviewId);
        ri.setFileName("tampered_img.png");
        ri.setFilePath(result.filePath());
        ri.setContentType("image/png");
        ri.setFileSize(imageData.length);
        ri.setChecksum("0000000000000000000000000000000000000000000000000000000000000000");
        ri.setDisplayOrder(1);
        ri.setUploadedAt(LocalDateTime.now());
        reviewImageRepository.save(ri);

        // Mismatched checksum — should be rejected (403 from integrity failure)
        mockMvc.perform(get("/storage/" + result.filePath()))
               .andExpect(status().isForbidden());
    }

    private String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(data));
    }

    private byte[] createMinimalPng() {
        return new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
            (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
            0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
            0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
            (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
            0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
        };
    }
}
