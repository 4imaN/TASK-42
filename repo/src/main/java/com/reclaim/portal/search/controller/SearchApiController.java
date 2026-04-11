package com.reclaim.portal.search.controller;

import com.reclaim.portal.search.entity.SearchTrend;
import com.reclaim.portal.search.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class SearchApiController {

    private final SearchService searchService;

    public SearchApiController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/autocomplete")
    public ResponseEntity<List<String>> autocomplete(@RequestParam("q") String q) {
        List<String> suggestions = searchService.getAutocompleteSuggestions(q);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/trending")
    public ResponseEntity<List<SearchTrend>> trending() {
        List<SearchTrend> trends = searchService.getTrendingSearches();
        return ResponseEntity.ok(trends);
    }
}
