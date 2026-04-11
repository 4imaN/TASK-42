package com.reclaim.portal.reviews.repository;

import com.reclaim.portal.reviews.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByOrderId(Long orderId);

    List<Review> findByReviewerUserId(Long reviewerUserId);

    boolean existsByOrderId(Long orderId);

    /**
     * Returns all reviews for orders that contain items from the given seller.
     * Used to compute review-sentiment signals for ranking.
     */
    @Query("SELECT r FROM Review r WHERE r.orderId IN " +
           "(SELECT oi.orderId FROM OrderItem oi WHERE oi.itemId IN " +
           "(SELECT ri.id FROM RecyclingItem ri WHERE ri.sellerId = :sellerId))")
    List<Review> findReviewsForSeller(@Param("sellerId") Long sellerId);
}
