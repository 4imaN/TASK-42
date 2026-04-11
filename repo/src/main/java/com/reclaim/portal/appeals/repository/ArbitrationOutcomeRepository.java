package com.reclaim.portal.appeals.repository;

import com.reclaim.portal.appeals.entity.ArbitrationOutcome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ArbitrationOutcomeRepository extends JpaRepository<ArbitrationOutcome, Long> {

    Optional<ArbitrationOutcome> findByAppealId(Long appealId);
}
