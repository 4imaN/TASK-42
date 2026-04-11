package com.reclaim.portal.catalog.repository;

import com.reclaim.portal.catalog.entity.ItemFingerprint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemFingerprintRepository extends JpaRepository<ItemFingerprint, Long> {

    Optional<ItemFingerprint> findByFingerprintHash(String hash);

    List<ItemFingerprint> findByDuplicateStatusAndReviewedFalse(String status);

    Optional<ItemFingerprint> findByItemId(Long itemId);
}
