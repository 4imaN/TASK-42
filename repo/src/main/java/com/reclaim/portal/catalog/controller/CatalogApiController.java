package com.reclaim.portal.catalog.controller;

import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.service.CatalogService;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.catalog.service.CatalogService.SearchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/catalog")
public class CatalogApiController {

    private final CatalogService catalogService;
    private final UserRepository userRepository;

    public CatalogApiController(CatalogService catalogService, UserRepository userRepository) {
        this.catalogService = catalogService;
        this.userRepository = userRepository;
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String condition,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        SearchResult result = catalogService.searchItems(
                keyword, category, condition, minPrice, maxPrice, userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", result.items());
        body.put("searchLogId", result.searchLogId());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecyclingItem> getItem(@PathVariable Long id) {
        RecyclingItem item = catalogService.getItemById(id);
        return ResponseEntity.ok(item);
    }

    @PostMapping("/click")
    public ResponseEntity<Void> logClick(@RequestBody Map<String, Long> body,
                                         Authentication authentication) {
        Long userId = resolveUserId(authentication);
        Long searchLogId = body.get("searchLogId");
        Long itemId = body.get("itemId");
        catalogService.logClick(userId, searchLogId, itemId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/check-duplicate")
    public ResponseEntity<Map<String, String>> checkDuplicate(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String attributes = body.get("attributes");
        String status = catalogService.checkDuplicate(title, attributes);
        return ResponseEntity.ok(Map.of("status", status));
    }

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found: " + userDetails.getUsername()));
        return user.getId();
    }
}
