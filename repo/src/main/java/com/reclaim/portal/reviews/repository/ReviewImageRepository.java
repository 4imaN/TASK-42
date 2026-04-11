package com.reclaim.portal.reviews.repository;

import com.reclaim.portal.reviews.entity.ReviewImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReviewImageRepository extends JpaRepository<ReviewImage, Long> {

    List<ReviewImage> findByReviewId(Long reviewId);

    long countByReviewId(Long reviewId);

    List<ReviewImage> findByFilePath(String filePath);
}
