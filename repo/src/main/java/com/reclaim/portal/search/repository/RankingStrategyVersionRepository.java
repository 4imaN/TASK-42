package com.reclaim.portal.search.repository;

import com.reclaim.portal.search.entity.RankingStrategyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankingStrategyVersionRepository extends JpaRepository<RankingStrategyVersion, Long> {

    Optional<RankingStrategyVersion> findByActiveTrue();

    List<RankingStrategyVersion> findAllByOrderByCreatedAtDesc();
}
