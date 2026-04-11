package com.reclaim.portal.catalog.repository;

import com.reclaim.portal.catalog.entity.SellerMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SellerMetricsRepository extends JpaRepository<SellerMetrics, Long> {

    Optional<SellerMetrics> findBySellerId(Long sellerId);
}
