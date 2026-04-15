package com.reclaim.portal.common.config;

import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies that {@link WebConfig} and its Thymeleaf-related beans are present in the context.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebConfigTest {

    @Autowired
    private ApplicationContext applicationContext;

    // -------------------------------------------------------------------------
    // Test 1: WebConfig bean itself loads without error
    // -------------------------------------------------------------------------

    @Test
    void shouldLoadWebConfigBean() {
        assertThatCode(() -> applicationContext.getBean(WebConfig.class))
                .doesNotThrowAnyException();
        assertThat(applicationContext.getBean(WebConfig.class)).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test 2: LayoutDialect bean contributed by WebConfig is present
    // -------------------------------------------------------------------------

    @Test
    void shouldExposeLayoutDialectBean() {
        assertThatCode(() -> applicationContext.getBean(LayoutDialect.class))
                .doesNotThrowAnyException();
        assertThat(applicationContext.getBean(LayoutDialect.class)).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Test 3: Thymeleaf template engine is configured
    // -------------------------------------------------------------------------

    @Test
    void shouldHaveThymeleafTemplateEngine() {
        assertThatCode(() -> applicationContext.getBean(SpringTemplateEngine.class))
                .doesNotThrowAnyException();
    }
}
