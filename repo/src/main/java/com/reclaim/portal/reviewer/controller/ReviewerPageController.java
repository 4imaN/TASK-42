package com.reclaim.portal.reviewer.controller;

import com.reclaim.portal.catalog.service.CatalogService;
import com.reclaim.portal.contracts.entity.ContractTemplate;
import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import com.reclaim.portal.contracts.service.ContractService;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.entity.OrderItem;
import com.reclaim.portal.orders.repository.OrderItemRepository;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.orders.service.OrderService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/reviewer")
@PreAuthorize("hasAnyRole('REVIEWER','ADMIN')")
public class ReviewerPageController {

    private final OrderService orderService;
    private final OrderItemRepository orderItemRepository;
    private final OrderRepository orderRepository;
    private final ContractService contractService;
    private final CatalogService catalogService;

    public ReviewerPageController(OrderService orderService,
                                  OrderItemRepository orderItemRepository,
                                  OrderRepository orderRepository,
                                  ContractService contractService,
                                  CatalogService catalogService) {
        this.orderService = orderService;
        this.orderItemRepository = orderItemRepository;
        this.orderRepository = orderRepository;
        this.contractService = contractService;
        this.catalogService = catalogService;
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Order> queue = orderService.getReviewerQueue();
        model.addAttribute("queueCount", queue.size());
        model.addAttribute("acceptedCount", orderRepository.countByOrderStatus("ACCEPTED"));
        model.addAttribute("completedCount", orderRepository.countByOrderStatus("COMPLETED"));
        return "reviewer/dashboard";
    }

    @GetMapping("/queue")
    public String queue(Model model) {
        model.addAttribute("queue", orderService.getReviewerQueue());
        return "reviewer/queue";
    }

    @GetMapping("/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model) {
        Order order = orderService.findOrderById(id);
        List<OrderItem> items = orderItemRepository.findByOrderId(id);
        model.addAttribute("order", order);
        model.addAttribute("items", items);
        model.addAttribute("categories", catalogService.getCategories());

        // Build list of {versionId, label} for the latest version of each active template
        List<Map<String, Object>> templateVersionOptions = new ArrayList<>();
        for (ContractTemplate tpl : contractService.getActiveTemplates()) {
            List<ContractTemplateVersion> versions = contractService.getTemplateVersions(tpl.getId());
            if (!versions.isEmpty()) {
                ContractTemplateVersion latest = versions.get(0); // sorted desc by versionNumber
                templateVersionOptions.add(Map.of(
                    "versionId", latest.getId(),
                    "label", tpl.getName() + " v" + latest.getVersionNumber()
                ));
            }
        }
        model.addAttribute("templateVersionOptions", templateVersionOptions);
        return "reviewer/order-detail";
    }
}
