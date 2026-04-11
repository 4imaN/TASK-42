package com.reclaim.portal.common.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;

@Configuration
public class StorageConfig {

    private final ReclaimProperties reclaimProperties;

    public StorageConfig(ReclaimProperties reclaimProperties) {
        this.reclaimProperties = reclaimProperties;
    }

    @Bean
    public ApplicationRunner storageInitializer() {
        return args -> {
            String rootPath = reclaimProperties.getStorage().getRootPath();
            Files.createDirectories(Paths.get(rootPath));
        };
    }
}
