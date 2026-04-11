package com.reclaim.portal.search.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ranking_strategy_versions")
public class RankingStrategyVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_label")
    private String versionLabel;

    @Column(name = "credit_score_weight")
    private BigDecimal creditScoreWeight;

    @Column(name = "positive_rate_weight")
    private BigDecimal positiveRateWeight;

    @Column(name = "review_quality_weight")
    private BigDecimal reviewQualityWeight;

    @Column(name = "min_credit_score_threshold")
    private BigDecimal minCreditScoreThreshold;

    @Column(name = "min_positive_rate_threshold")
    private BigDecimal minPositiveRateThreshold;

    private boolean active;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public RankingStrategyVersion() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getVersionLabel() {
        return versionLabel;
    }

    public void setVersionLabel(String versionLabel) {
        this.versionLabel = versionLabel;
    }

    public BigDecimal getCreditScoreWeight() {
        return creditScoreWeight;
    }

    public void setCreditScoreWeight(BigDecimal creditScoreWeight) {
        this.creditScoreWeight = creditScoreWeight;
    }

    public BigDecimal getPositiveRateWeight() {
        return positiveRateWeight;
    }

    public void setPositiveRateWeight(BigDecimal positiveRateWeight) {
        this.positiveRateWeight = positiveRateWeight;
    }

    public BigDecimal getReviewQualityWeight() {
        return reviewQualityWeight;
    }

    public void setReviewQualityWeight(BigDecimal reviewQualityWeight) {
        this.reviewQualityWeight = reviewQualityWeight;
    }

    public BigDecimal getMinCreditScoreThreshold() {
        return minCreditScoreThreshold;
    }

    public void setMinCreditScoreThreshold(BigDecimal minCreditScoreThreshold) {
        this.minCreditScoreThreshold = minCreditScoreThreshold;
    }

    public BigDecimal getMinPositiveRateThreshold() {
        return minPositiveRateThreshold;
    }

    public void setMinPositiveRateThreshold(BigDecimal minPositiveRateThreshold) {
        this.minPositiveRateThreshold = minPositiveRateThreshold;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
