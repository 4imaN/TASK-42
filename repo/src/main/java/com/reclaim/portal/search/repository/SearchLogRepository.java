package com.reclaim.portal.search.repository;

import com.reclaim.portal.search.entity.SearchLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    List<SearchLog> findByUserIdOrderBySearchedAtDesc(Long userId);

    List<SearchLog> findTop20ByUserIdOrderBySearchedAtDesc(Long userId);

    long count();
}
