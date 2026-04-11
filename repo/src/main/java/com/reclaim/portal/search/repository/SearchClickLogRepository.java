package com.reclaim.portal.search.repository;

import com.reclaim.portal.search.entity.SearchClickLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchClickLogRepository extends JpaRepository<SearchClickLog, Long> {

    long countByItemId(Long itemId);
}
