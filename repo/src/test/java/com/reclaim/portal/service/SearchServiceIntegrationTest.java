package com.reclaim.portal.service;

import com.reclaim.portal.search.entity.SearchTrend;
import com.reclaim.portal.search.repository.SearchTrendRepository;
import com.reclaim.portal.search.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SearchServiceIntegrationTest {

    @Autowired
    private SearchService searchService;

    @Autowired
    private SearchTrendRepository searchTrendRepository;

    @Test
    void shouldUpdateTrends() {
        String term = "recycling bottles test " + System.nanoTime();

        // First call creates a new trend
        searchService.updateTrends(term);

        SearchTrend trend = searchTrendRepository.findBySearchTerm(term).orElseThrow();
        assertThat(trend.getSearchCount()).isEqualTo(1);

        // Second call increments the count
        searchService.updateTrends(term);

        SearchTrend updated = searchTrendRepository.findBySearchTerm(term).orElseThrow();
        assertThat(updated.getSearchCount()).isEqualTo(2);
    }

    @Test
    void shouldGetTrending() {
        // Seed some trends
        for (int i = 1; i <= 5; i++) {
            SearchTrend trend = new SearchTrend();
            trend.setSearchTerm("trending term " + i);
            trend.setSearchCount(i * 10);
            trend.setLastSearchedAt(LocalDateTime.now());
            trend.setPeriodStart(LocalDate.now());
            trend.setPeriodEnd(LocalDate.now());
            searchTrendRepository.save(trend);
        }

        List<SearchTrend> trending = searchService.getTrendingSearches();

        assertThat(trending).isNotEmpty();
        // Should be in descending order of search count
        for (int i = 0; i < trending.size() - 1; i++) {
            assertThat(trending.get(i).getSearchCount())
                .isGreaterThanOrEqualTo(trending.get(i + 1).getSearchCount());
        }
    }

    @Test
    void shouldGetAutocomplete() {
        String unique = "uniqueterm" + System.nanoTime();
        SearchTrend trend = new SearchTrend();
        trend.setSearchTerm(unique + "abc");
        trend.setSearchCount(5);
        trend.setLastSearchedAt(LocalDateTime.now());
        trend.setPeriodStart(LocalDate.now());
        trend.setPeriodEnd(LocalDate.now());
        searchTrendRepository.save(trend);

        List<String> suggestions = searchService.getAutocompleteSuggestions(unique);

        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions).anyMatch(s -> s.contains(unique));
    }
}
