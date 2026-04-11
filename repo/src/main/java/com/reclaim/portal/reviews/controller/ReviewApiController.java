package com.reclaim.portal.reviews.controller;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.entity.ReviewImage;
import com.reclaim.portal.reviews.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {

    // -------------------------------------------------------------------------
    // Request body record type
    // -------------------------------------------------------------------------
    public record CreateReviewRequest(
        @jakarta.validation.constraints.NotNull Long orderId,
        @jakarta.validation.constraints.Min(1) @jakarta.validation.constraints.Max(5) int rating,
        @jakarta.validation.constraints.Size(max = 1000) String reviewText
    ) {}

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------
    private final ReviewService reviewService;
    private final UserRepository userRepository;

    public ReviewApiController(ReviewService reviewService, UserRepository userRepository) {
        this.reviewService = reviewService;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // POST / — create a review
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Review> createReview(@Valid @RequestBody CreateReviewRequest request,
                                               Authentication authentication) {
        Long userId = resolveUserId(authentication);
        Review review = reviewService.createReview(
            request.orderId(), userId, request.rating(), request.reviewText());
        return ResponseEntity.ok(review);
    }

    // -------------------------------------------------------------------------
    // POST /{id}/images — upload image to a review
    // -------------------------------------------------------------------------
    @PostMapping("/{id}/images")
    public ResponseEntity<ReviewImage> addReviewImage(@PathVariable Long id,
                                                       @RequestParam("file") MultipartFile file,
                                                       Authentication authentication) {
        Long userId = resolveUserId(authentication);
        ReviewImage image = reviewService.addReviewImage(id, userId, file);
        return ResponseEntity.ok(image);
    }

    // -------------------------------------------------------------------------
    // GET /order/{orderId} — get review for a specific order
    // -------------------------------------------------------------------------
    @GetMapping("/order/{orderId}")
    public ResponseEntity<Map<String, Object>> getReviewForOrder(@PathVariable Long orderId,
                                                                   Authentication authentication) {
        Long actorId = resolveUserId(authentication);
        boolean staff = isStaff(authentication);
        return ResponseEntity.ok(reviewService.getReviewForOrder(orderId, actorId, staff));
    }

    // -------------------------------------------------------------------------
    // GET /my — reviews by the authenticated user
    // -------------------------------------------------------------------------
    @GetMapping("/my")
    public ResponseEntity<List<Review>> getMyReviews(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(reviewService.getReviewsByUser(userId));
    }

    // -------------------------------------------------------------------------
    // Helper: extract User id from JWT principal
    // -------------------------------------------------------------------------
    private Long resolveUserId(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new EntityNotFoundException(
                "User not found: " + userDetails.getUsername()));
        return user.getId();
    }

    // -------------------------------------------------------------------------
    // Helper: check if authenticated user has staff role (REVIEWER or ADMIN)
    // -------------------------------------------------------------------------
    private boolean isStaff(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(a -> a.equals("ROLE_REVIEWER") || a.equals("ROLE_ADMIN"));
    }
}
