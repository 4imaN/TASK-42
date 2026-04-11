package com.reclaim.portal.users.controller;

import com.reclaim.portal.appeals.service.AppealService;
import com.reclaim.portal.auth.entity.User;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.catalog.entity.RecyclingItem;
import com.reclaim.portal.catalog.service.CatalogService;
import com.reclaim.portal.contracts.entity.ContractInstance;
import com.reclaim.portal.contracts.entity.SignatureArtifact;
import com.reclaim.portal.contracts.service.ContractService;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.service.OrderService;
import com.reclaim.portal.reviews.service.ReviewService;
import com.reclaim.portal.search.service.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class UserPageController {

    private final OrderService orderService;
    private final ContractService contractService;
    private final UserRepository userRepository;
    private final ReviewService reviewService;
    private final CatalogService catalogService;
    private final SearchService searchService;
    private final AppealService appealService;

    public UserPageController(OrderService orderService,
                              ContractService contractService,
                              UserRepository userRepository,
                              ReviewService reviewService,
                              CatalogService catalogService,
                              SearchService searchService,
                              AppealService appealService) {
        this.orderService = orderService;
        this.contractService = contractService;
        this.userRepository = userRepository;
        this.reviewService = reviewService;
        this.catalogService = catalogService;
        this.searchService = searchService;
        this.appealService = appealService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/user/dashboard";
    }

    @GetMapping("/user/dashboard")
    public String dashboard(Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        if (userId != null) {
            List<Order> allOrders = orderService.getOrdersByUser(userId);
            model.addAttribute("orderCount", allOrders.size());
            model.addAttribute("pendingOrders", allOrders.stream()
                .filter(o -> "PENDING_CONFIRMATION".equals(o.getOrderStatus())).count());
            model.addAttribute("completedOrders", allOrders.stream()
                .filter(o -> "COMPLETED".equals(o.getOrderStatus())).count());
            model.addAttribute("reviewCount", reviewService.getReviewsByUser(userId).size());
            model.addAttribute("recentOrders", allOrders.stream().limit(5).toList());
        }
        return "user/dashboard";
    }

    @GetMapping("/user/search")
    public String search(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String category,
                         @RequestParam(required = false) String condition,
                         @RequestParam(required = false) BigDecimal minPrice,
                         @RequestParam(required = false) BigDecimal maxPrice,
                         Model model, Authentication auth) {
        model.addAttribute("categories", catalogService.getCategories());
        model.addAttribute("trendingTerms", searchService.getTrendingSearches());
        boolean hasFilters = (keyword != null && !keyword.isBlank())
                || category != null || condition != null || minPrice != null || maxPrice != null;
        if (hasFilters) {
            Long userId = resolveUserId(auth);
            CatalogService.SearchResult searchResult = catalogService.searchItems(keyword, category, condition, minPrice, maxPrice, userId);
            model.addAttribute("results", searchResult.items());
            model.addAttribute("searchLogId", searchResult.searchLogId());
        }
        return "user/search";
    }

    @GetMapping("/user/orders")
    public String orders(Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        List<Order> orders = userId != null
                ? orderService.getOrdersByUser(userId)
                : Collections.emptyList();
        model.addAttribute("orders", orders);
        return "user/orders";
    }

    @GetMapping("/user/orders/create")
    public String createOrder() {
        return "user/create-order";
    }

    @GetMapping("/user/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        Map<String, Object> data = orderService.getOrderWithLogs(id, userId, staff);
        model.addAttribute("order", data.get("order"));
        model.addAttribute("items", data.get("items"));
        model.addAttribute("logs", data.get("logs"));
        return "user/order-detail";
    }

    @GetMapping("/user/reviews/create")
    public String createReview(@RequestParam(required = false) Long orderId, Model model, Authentication auth) {
        if (orderId != null) {
            Long userId = resolveUserId(auth);
            if (userId != null) {
                try {
                    boolean staff = isStaff(auth);
                    Map<String, Object> data = orderService.getOrderWithLogs(orderId, userId, staff);
                    model.addAttribute("order", data.get("order"));
                } catch (Exception e) {
                    // Order not found or access denied — page still renders without context
                }
            }
        }
        return "user/create-review";
    }

    @GetMapping("/user/appeals")
    public String appeals(Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        if (userId != null) {
            model.addAttribute("appeals", appealService.getAppealsForUser(userId));
        }
        return "user/appeals";
    }

    @GetMapping("/user/appeals/{id}")
    public String appealDetail(@PathVariable Long id, Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        Map<String, Object> details = appealService.getAppealDetails(id, userId, staff);
        model.addAttribute("appeal", details.get("appeal"));
        model.addAttribute("evidence", details.get("evidence"));
        model.addAttribute("outcome", details.get("outcome"));
        return "user/appeal-detail";
    }

    @GetMapping("/user/appeals/create")
    public String createAppeal(@RequestParam(required = false) Long orderId, Model model) {
        model.addAttribute("orderId", orderId);
        return "user/create-appeal";
    }

    @GetMapping("/user/reviews")
    public String reviews(Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        if (userId != null) {
            model.addAttribute("reviews", reviewService.getReviewsByUser(userId));
        }
        return "user/reviews";
    }

    @GetMapping("/user/contracts")
    public String contracts(@RequestParam(required = false) String status,
                            Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        List<ContractInstance> contracts = userId != null
                ? contractService.getUserContracts(userId)
                : Collections.emptyList();
        // Populate transient displayStatus without overwriting persisted workflow state
        contractService.populateDisplayStatus(contracts);
        // Filter by status if requested (matches either workflow state or display state)
        if (status != null && !status.isBlank()) {
            contracts = contracts.stream()
                    .filter(c -> status.equals(c.getContractStatus())
                              || status.equals(c.getDisplayStatus()))
                    .toList();
        }
        model.addAttribute("contracts", contracts);
        return "contract/list";
    }

    @GetMapping("/user/contracts/{id}")
    public String contractDetail(@PathVariable Long id, Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        ContractInstance contract = contractService.getContractDetail(id, userId, staff);
        contractService.populateDisplayStatus(contract);
        model.addAttribute("contract", contract);
        model.addAttribute("evidenceFiles", contractService.getContractEvidence(id, userId, staff));
        model.addAttribute("signatureArtifact", contractService.getSignatureArtifact(id));
        return "contract/detail";
    }

    @GetMapping("/user/contracts/{id}/sign")
    public String signContract(@PathVariable Long id, Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        ContractInstance contract = contractService.getContractDetail(id, userId, staff);
        contractService.populateDisplayStatus(contract);

        // Only allow access to the sign page when the contract is in CONFIRMED state
        if (!"CONFIRMED".equals(contract.getContractStatus())) {
            return "redirect:/user/contracts/" + id;
        }

        model.addAttribute("contract", contract);
        return "contract/sign";
    }

    @GetMapping("/user/contracts/{id}/print")
    public String printContract(@PathVariable Long id, Model model, Authentication auth) {
        Long userId = resolveUserId(auth);
        boolean staff = isStaff(auth);
        ContractInstance contract = contractService.getPrintableContract(id, userId, staff);
        contractService.populateDisplayStatus(contract);
        SignatureArtifact signature = contractService.getSignatureArtifact(id);
        model.addAttribute("contract", contract);
        model.addAttribute("signature", signature);
        return "contract/print";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        String username = (principal instanceof UserDetails ud)
                ? ud.getUsername()
                : principal.toString();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElse(null);
    }

    private boolean isStaff(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_REVIEWER") || a.equals("ROLE_ADMIN"));
    }
}
