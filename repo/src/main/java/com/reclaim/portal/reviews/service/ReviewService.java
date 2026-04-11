package com.reclaim.portal.reviews.service;

import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.reviews.entity.Review;
import com.reclaim.portal.reviews.entity.ReviewImage;
import com.reclaim.portal.reviews.repository.ReviewImageRepository;
import com.reclaim.portal.reviews.repository.ReviewRepository;
import com.reclaim.portal.storage.dto.StorageResult;
import com.reclaim.portal.storage.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ReviewService {

    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final int MAX_IMAGES_PER_REVIEW = 5;
    private static final int MAX_REVIEW_TEXT_LENGTH = 1000;
    private static final String REVIEWS_SUBDIR = "reviews";

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final OrderRepository orderRepository;
    private final StorageService storageService;

    public ReviewService(ReviewRepository reviewRepository,
                         ReviewImageRepository reviewImageRepository,
                         OrderRepository orderRepository,
                         StorageService storageService) {
        this.reviewRepository = reviewRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
    }

    /**
     * Create a review for a completed order.
     * Validates: order exists, order is COMPLETED, no existing review, rating 1-5, text <= 1000 chars.
     */
    @Transactional
    public Review createReview(Long orderId, Long userId, int rating, String reviewText) {
        // Validate order exists
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        // Order must be COMPLETED
        if (!STATUS_COMPLETED.equals(order.getOrderStatus())) {
            throw new BusinessRuleException(
                "Reviews can only be created for completed orders. Current status: "
                    + order.getOrderStatus());
        }

        // Only order owner can review
        if (!userId.equals(order.getUserId())) {
            throw new BusinessRuleException("Access denied: only the order owner can create a review");
        }

        // No existing review
        if (reviewRepository.existsByOrderId(orderId)) {
            throw new BusinessRuleException("A review already exists for order id: " + orderId);
        }

        // Rating 1-5
        if (rating < 1 || rating > 5) {
            throw new BusinessRuleException("Rating must be between 1 and 5");
        }

        // Text length
        if (reviewText != null && reviewText.length() > MAX_REVIEW_TEXT_LENGTH) {
            throw new BusinessRuleException(
                "Review text must not exceed " + MAX_REVIEW_TEXT_LENGTH + " characters");
        }

        LocalDateTime now = LocalDateTime.now();
        Review review = new Review();
        review.setOrderId(orderId);
        review.setReviewerUserId(userId);
        review.setRating(rating);
        review.setReviewText(reviewText);
        review.setCreatedAt(now);
        review.setUpdatedAt(now);

        return reviewRepository.save(review);
    }

    /**
     * Add an image to an existing review.
     * Validates: review exists, belongs to user, image count < 5.
     */
    @Transactional
    public ReviewImage addReviewImage(Long reviewId, Long userId, MultipartFile file) {
        // Validate review exists
        Review review = reviewRepository.findById(reviewId)
            .orElseThrow(() -> new EntityNotFoundException("Review", reviewId));

        // Review must belong to the user
        if (!userId.equals(review.getReviewerUserId())) {
            throw new BusinessRuleException("You can only add images to your own reviews");
        }

        // Max 5 images
        long imageCount = reviewImageRepository.countByReviewId(reviewId);
        if (imageCount >= MAX_IMAGES_PER_REVIEW) {
            throw new BusinessRuleException(
                "A review cannot have more than " + MAX_IMAGES_PER_REVIEW + " images");
        }

        // Store the file
        StorageResult result = storageService.store(file, REVIEWS_SUBDIR);

        ReviewImage image = new ReviewImage();
        image.setReviewId(reviewId);
        image.setFileName(result.fileName());
        image.setFilePath(result.filePath());
        image.setContentType(result.contentType());
        image.setFileSize(result.fileSize());
        image.setChecksum(result.checksum());
        image.setDisplayOrder((int) imageCount); // append at end
        image.setUploadedAt(LocalDateTime.now());

        return reviewImageRepository.save(image);
    }

    /**
     * Get review and associated images for an order.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewForOrder(Long orderId) {
        Review review = reviewRepository.findByOrderId(orderId)
            .orElseThrow(() -> new EntityNotFoundException(
                "Review not found for order id: " + orderId));
        List<ReviewImage> images = reviewImageRepository.findByReviewId(review.getId());
        return Map.of("review", review, "images", images);
    }

    /**
     * Get review for an order, enforcing ownership for non-staff callers.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewForOrder(Long orderId, Long actorId, boolean isStaff) {
        // check order ownership if not staff
        if (!isStaff) {
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
            if (!actorId.equals(order.getUserId())) {
                throw new BusinessRuleException("Access denied to review");
            }
        }
        return getReviewForOrder(orderId);
    }

    /**
     * Get all reviews by a specific user.
     */
    @Transactional(readOnly = true)
    public List<Review> getReviewsByUser(Long userId) {
        return reviewRepository.findByReviewerUserId(userId);
    }
}
