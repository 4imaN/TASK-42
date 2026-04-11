package com.reclaim.portal.contracts.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Redirects bare /contracts/** routes to the user-scoped /user/contracts/** routes
 * that properly populate model data and enforce authorization.
 */
@Controller
@RequestMapping("/contracts")
public class ContractPageController {

    @GetMapping
    public String list() {
        return "redirect:/user/contracts";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id) {
        return "redirect:/user/contracts/" + id;
    }

    @GetMapping("/{id}/sign")
    public String sign(@PathVariable Long id) {
        return "redirect:/user/contracts/" + id + "/sign";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id) {
        return "redirect:/user/contracts/" + id + "/print";
    }
}
