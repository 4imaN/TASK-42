package com.reclaim.portal.admin.controller;

import com.reclaim.portal.admin.service.AdminService;
import com.reclaim.portal.auth.repository.UserRepository;
import com.reclaim.portal.contracts.service.ContractService;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.search.repository.SearchLogRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@RequestMapping("/admin")
public class AdminPageController {

    private final AdminService adminService;
    private final ContractService contractService;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SearchLogRepository searchLogRepository;

    public AdminPageController(AdminService adminService, ContractService contractService,
                               UserRepository userRepository, OrderRepository orderRepository,
                               SearchLogRepository searchLogRepository) {
        this.adminService = adminService;
        this.contractService = contractService;
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
        this.searchLogRepository = searchLogRepository;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalOrders", orderRepository.count());
        model.addAttribute("totalSearches", searchLogRepository.count());
        model.addAttribute("activeTemplates", contractService.getActiveTemplates().size());
        return "admin/dashboard";
    }

    @GetMapping("/strategies")
    public String strategies(Model model) {
        model.addAttribute("strategies", adminService.getStrategies());
        return "admin/strategies";
    }

    @GetMapping("/analytics")
    public String analytics(Model model) {
        Map<String, Object> analytics = adminService.getSearchAnalytics();
        model.addAllAttributes(analytics);
        return "admin/analytics";
    }

    @GetMapping("/templates")
    public String templates(Model model) {
        model.addAttribute("templates", contractService.getActiveTemplates());
        return "admin/templates";
    }
}
