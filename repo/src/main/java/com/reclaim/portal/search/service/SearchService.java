package com.reclaim.portal.search.service;

import com.reclaim.portal.search.entity.SearchTrend;
import com.reclaim.portal.search.repository.SearchTrendRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class SearchService {

    private final SearchTrendRepository searchTrendRepository;

    public SearchService(SearchTrendRepository searchTrendRepository) {
        this.searchTrendRepository = searchTrendRepository;
    }

    @Transactional(readOnly = true)
    public List<String> getAutocompleteSuggestions(String partial) {
        return searchTrendRepository.findBySearchTermContainingIgnoreCase(partial)
                .stream()
                .map(SearchTrend::getSearchTerm)
                .limit(10)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<SearchTrend> getTrendingSearches() {
        return searchTrendRepository.findTop10ByOrderBySearchCountDesc();
    }

    public void updateTrends(String searchTerm) {
        Optional<SearchTrend> existing = searchTrendRepository.findBySearchTerm(searchTerm);
        if (existing.isPresent()) {
            SearchTrend trend = existing.get();
            trend.setSearchCount(trend.getSearchCount() + 1);
            trend.setLastSearchedAt(LocalDateTime.now());
            searchTrendRepository.save(trend);
        } else {
            SearchTrend trend = new SearchTrend();
            trend.setSearchTerm(searchTerm);
            trend.setSearchCount(1);
            trend.setLastSearchedAt(LocalDateTime.now());
            trend.setPeriodStart(LocalDate.now());
            trend.setPeriodEnd(LocalDate.now());
            searchTrendRepository.save(trend);
        }
    }
}
