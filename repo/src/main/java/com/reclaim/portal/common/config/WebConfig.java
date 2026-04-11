package com.reclaim.portal.common.config;

import jakarta.servlet.http.HttpServletRequest;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@Configuration
public class WebConfig {

    @Bean
    public LayoutDialect layoutDialect() {
        return new LayoutDialect();
    }

    /**
     * Exposes the current request URI to all Thymeleaf templates as {@code ${currentURI}}.
     * Thymeleaf 3.1 removed {@code #httpServletRequest} from SpEL expressions for security,
     * so the layout nav links need this model attribute instead.
     */
    @ControllerAdvice
    static class RequestUriAdvice {
        @ModelAttribute("currentURI")
        public String currentURI(HttpServletRequest request) {
            return request.getRequestURI();
        }
    }
}
