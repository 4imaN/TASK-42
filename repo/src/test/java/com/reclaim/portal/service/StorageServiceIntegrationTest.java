package com.reclaim.portal.service;

import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.storage.dto.StorageResult;
import com.reclaim.portal.storage.service.StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class StorageServiceIntegrationTest {

    @TempDir
    static Path tempDir;

    @DynamicPropertySource
    static void overrideStoragePath(DynamicPropertyRegistry registry) {
        registry.add("reclaim.storage.root-path", () -> tempDir.toString());
    }

    @Autowired
    private StorageService storageService;

    // Minimal JPEG header (SOI + APP0 marker)
    private static final byte[] MINIMAL_JPEG = {
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
        0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00,
        0x00, 0x01, 0x00, 0x01, 0x00, 0x00
    };

    // Minimal 1x1 PNG bytes
    private static final byte[] MINIMAL_PNG = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
        0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
        0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53,
        (byte) 0xDE, 0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41,
        0x54, 0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xFF, (byte) 0xFF, 0x3F,
        0x00, 0x05, (byte) 0xFE, 0x02, (byte) 0xFE, (byte) 0xDC, (byte) 0xCC, 0x59,
        (byte) 0xE7, 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E,
        0x44, (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    void shouldStoreAndRetrieve() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "test-image.png", "image/png", MINIMAL_PNG);

        StorageResult result = storageService.store(file, "test-subdir");

        assertThat(result).isNotNull();
        assertThat(result.filePath()).startsWith("test-subdir/");
        assertThat(result.checksum()).isNotBlank();
        assertThat(result.fileName()).isEqualTo("test-image.png");
        assertThat(result.fileSize()).isEqualTo(MINIMAL_PNG.length);

        // Retrieve the stored file
        byte[] retrieved = storageService.retrieve(result.filePath());
        assertThat(retrieved).isEqualTo(MINIMAL_PNG);
    }

    @Test
    void shouldBlockPathTraversal() {
        assertThatThrownBy(() -> storageService.retrieve("../etc/passwd"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void shouldRejectOversizedFile() {
        // Create content larger than 3MB limit (3145728 bytes)
        byte[] oversized = new byte[3145729];
        // Put PNG header at start to avoid extension error
        System.arraycopy(MINIMAL_PNG, 0, oversized, 0, MINIMAL_PNG.length);

        MockMultipartFile file = new MockMultipartFile(
            "file", "big-file.png", "image/png", oversized);

        assertThatThrownBy(() -> storageService.store(file, "test-subdir"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("maximum allowed size");
    }

    @Test
    void shouldVerifyChecksum() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "checksum-test.png", "image/png", MINIMAL_PNG);

        StorageResult result = storageService.store(file, "checksum-subdir");

        boolean valid = storageService.verifyChecksum(result.filePath(), result.checksum());
        assertThat(valid).isTrue();
    }

    @Test
    void shouldFailChecksumVerificationForWrongHash() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "checksum-bad.png", "image/png", MINIMAL_PNG);

        StorageResult result = storageService.store(file, "checksum-bad-subdir");

        boolean valid = storageService.verifyChecksum(result.filePath(), "0000000000000000000000000000000000000000000000000000000000000000");
        assertThat(valid).isFalse();
    }

    @Test
    void shouldRejectDisallowedExtension() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "script.exe", "application/octet-stream", new byte[]{0x4D, 0x5A});

        assertThatThrownBy(() -> storageService.store(file, "test-subdir"))
            .isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining("not allowed");
    }

    @Test
    void shouldDeleteFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "delete-me.png", "image/png", MINIMAL_PNG);

        StorageResult result = storageService.store(file, "delete-subdir");

        // Should not throw
        storageService.delete(result.filePath());

        // Now retrieve should fail
        assertThatThrownBy(() -> storageService.retrieve(result.filePath()))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.store(emptyFile, "test-subdir"))
            .isInstanceOf(com.reclaim.portal.common.exception.BusinessRuleException.class)
            .hasMessageContaining("empty");
    }

    @Test
    void shouldRejectDeleteOfNonExistentFile() {
        assertThatThrownBy(() -> storageService.delete("nonexistent-subdir/no-such-file.png"))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldRejectRetrieveOfNonExistentFile() {
        assertThatThrownBy(() -> storageService.retrieve("nonexistent-subdir/missing.png"))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldRejectVerifyChecksumOfNonExistentFile() {
        assertThatThrownBy(() -> storageService.verifyChecksum(
                "nonexistent-subdir/ghost.png", "abc123"))
            .isInstanceOf(com.reclaim.portal.common.exception.EntityNotFoundException.class);
    }

    @Test
    void shouldStoreJpgFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpg", "image/jpeg", MINIMAL_JPEG);

        StorageResult result = storageService.store(file, "jpg-subdir");

        assertThat(result.filePath()).startsWith("jpg-subdir/");
        assertThat(result.filePath()).endsWith(".jpg");
    }

    @Test
    void shouldStoreJpegFile() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "photo.jpeg", "image/jpeg", MINIMAL_JPEG);

        StorageResult result = storageService.store(file, "jpeg-subdir");

        assertThat(result.filePath()).startsWith("jpeg-subdir/");
        assertThat(result.filePath()).endsWith(".jpeg");
    }

    @Test
    void shouldBlockPathTraversalOnDelete() {
        assertThatThrownBy(() -> storageService.delete("../sensitive/data"))
            .isInstanceOf(com.reclaim.portal.common.exception.BusinessRuleException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void shouldBlockPathTraversalOnVerifyChecksum() {
        assertThatThrownBy(() -> storageService.verifyChecksum("../etc/shadow", "deadbeef"))
            .isInstanceOf(com.reclaim.portal.common.exception.BusinessRuleException.class)
            .hasMessageContaining("traversal");
    }

    @Test
    void shouldStoreAndChecksumMatchReturnTrue() {
        MockMultipartFile file = new MockMultipartFile(
            "file", "verify-ok.png", "image/png", MINIMAL_PNG);

        StorageResult result = storageService.store(file, "verify-ok-subdir");

        assertThat(storageService.verifyChecksum(result.filePath(), result.checksum())).isTrue();
        assertThat(storageService.verifyChecksum(result.filePath(), "wrong-checksum")).isFalse();
    }
}
