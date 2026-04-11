package com.reclaim.portal.storage.dto;

public record StorageResult(
        String filePath,
        String checksum,
        String fileName,
        String contentType,
        long fileSize
) {}
