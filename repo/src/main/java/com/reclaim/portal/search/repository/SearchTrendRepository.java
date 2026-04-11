package com.reclaim.portal.search.repository;

import com.reclaim.portal.search.entity.SearchTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchTrendRepository extends JpaRepository<SearchTrend, Long> {

    List<SearchTrend> findTop10ByOrderBySearchCountDesc();

    List<SearchTrend> findBySearchTermContainingIgnoreCase(String partial);

    Optional<SearchTrend> findBySearchTerm(String searchTerm);
}
