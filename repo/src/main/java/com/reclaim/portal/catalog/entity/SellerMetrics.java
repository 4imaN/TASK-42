package com.reclaim.portal.catalog.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seller_metrics")
public class SellerMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "credit_score")
    private BigDecimal creditScore;

    @Column(name = "positive_rate")
    private BigDecimal positiveRate;

    @Column(name = "total_transactions")
    private int totalTransactions;

    @Column(name = "positive_transactions")
    private int positiveTransactions;

    @Column(name = "average_rating")
    private BigDecimal averageRating;

    @Column(name = "recent_review_quality")
    private BigDecimal recentReviewQuality;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SellerMetrics() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSellerId() {
        return sellerId;
    }

    public void setSellerId(Long sellerId) {
        this.sellerId = sellerId;
    }

    public BigDecimal getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(BigDecimal creditScore) {
        this.creditScore = creditScore;
    }

    public BigDecimal getPositiveRate() {
        return positiveRate;
    }

    public void setPositiveRate(BigDecimal positiveRate) {
        this.positiveRate = positiveRate;
    }

    public int getTotalTransactions() {
        return totalTransactions;
    }

    public void setTotalTransactions(int totalTransactions) {
        this.totalTransactions = totalTransactions;
    }

    public int getPositiveTransactions() {
        return positiveTransactions;
    }

    public void setPositiveTransactions(int positiveTransactions) {
        this.positiveTransactions = positiveTransactions;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public BigDecimal getRecentReviewQuality() {
        return recentReviewQuality;
    }

    public void setRecentReviewQuality(BigDecimal recentReviewQuality) {
        this.recentReviewQuality = recentReviewQuality;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
