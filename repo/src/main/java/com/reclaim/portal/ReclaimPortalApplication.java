package com.reclaim.portal;

import com.reclaim.portal.common.config.ReclaimProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ReclaimProperties.class)
public class ReclaimPortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReclaimPortalApplication.class, args);
    }
}
