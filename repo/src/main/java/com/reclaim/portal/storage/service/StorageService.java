package com.reclaim.portal.storage.service;

import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.storage.dto.StorageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private static final java.util.Map<String, String> EXTENSION_CONTENT_TYPE_MAP =
            java.util.Map.of(
                    "jpg", "image/jpeg",
                    "jpeg", "image/jpeg",
                    "png", "image/png"
            );

    private final ReclaimProperties reclaimProperties;

    public StorageService(ReclaimProperties reclaimProperties) {
        this.reclaimProperties = reclaimProperties;
    }

    /**
     * Validates and stores the given multipart file under a subdirectory of the configured storage root.
     *
     * @param file     the file to store
     * @param subDirectory subdirectory within the storage root (e.g. "signatures", "evidence")
     * @return a StorageResult record describing the stored file
     */
    public StorageResult store(MultipartFile file, String subDirectory) {
        ReclaimProperties.Storage storageConfig = reclaimProperties.getStorage();

        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("File must not be empty");
        }

        if (file.getSize() > storageConfig.getMaxFileSize()) {
            throw new BusinessRuleException("File size exceeds the maximum allowed size of "
                    + storageConfig.getMaxFileSize() + " bytes");
        }

        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";

        String extension = extractExtension(originalFilename).toLowerCase();
        List<String> allowedExtensions = storageConfig.getAllowedExtensionList();
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessRuleException("File extension '" + extension
                    + "' is not allowed. Allowed extensions: " + allowedExtensions);
        }

        String expectedContentType = EXTENSION_CONTENT_TYPE_MAP.get(extension);
        String actualContentType = file.getContentType();
        if (expectedContentType != null && !expectedContentType.equalsIgnoreCase(actualContentType)) {
            throw new BusinessRuleException("Content type '" + actualContentType
                    + "' does not match expected type '" + expectedContentType
                    + "' for extension '" + extension + "'");
        }

        // Read file bytes once — used for both magic byte validation and writing to disk
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to read file content");
        }

        // Validate file magic bytes to prevent content-type spoofing
        if (!hasValidMagicBytes(fileBytes, extension)) {
            throw new BusinessRuleException(
                    "File content does not match expected format for '" + extension + "'");
        }

        String rootPath = storageConfig.getRootPath();
        Path targetDir = Paths.get(rootPath, subDirectory);
        try {
            Files.createDirectories(targetDir);

            String storedFileName = UUID.randomUUID() + "." + extension;
            Path destination = targetDir.resolve(storedFileName);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new DigestInputStream(
                    new java.io.ByteArrayInputStream(fileBytes), digest)) {
                Files.copy(is, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            String relativeFilePath = subDirectory + "/" + storedFileName;

            return new StorageResult(
                    relativeFilePath,
                    checksum,
                    originalFilename,
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Failed to store file in {}: {}", subDirectory, e.getMessage());
            throw new RuntimeException("Failed to store file: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the raw bytes for a stored file.
     *
     * @param filePath the relative file path as returned by store()
     * @return byte array of file contents
     */
    public byte[] retrieve(String filePath) {
        Path resolvedPath = resolveAndValidatePath(filePath);
        if (!Files.exists(resolvedPath)) {
            throw new EntityNotFoundException("File not found: " + filePath);
        }
        try {
            return Files.readAllBytes(resolvedPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + filePath, e);
        }
    }

    /**
     * Verifies that the stored file matches the expected SHA-256 checksum.
     *
     * @param filePath         the relative file path
     * @param expectedChecksum the hex-encoded SHA-256 checksum to verify against
     * @return true if checksum matches, false otherwise
     */
    public boolean verifyChecksum(String filePath, String expectedChecksum) {
        Path resolvedPath = resolveAndValidatePath(filePath);
        if (!Files.exists(resolvedPath)) {
            throw new EntityNotFoundException("File not found: " + filePath);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = new DigestInputStream(Files.newInputStream(resolvedPath), digest)) {
                byte[] buffer = new byte[8192];
                while (is.read(buffer) != -1) {
                    // digest is updated by DigestInputStream
                }
            }
            String actualChecksum = HexFormat.of().formatHex(digest.digest());
            return actualChecksum.equalsIgnoreCase(expectedChecksum);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to verify checksum for file: " + filePath, e);
        }
    }

    /**
     * Deletes a stored file.
     *
     * @param filePath the relative file path as returned by store()
     */
    public void delete(String filePath) {
        Path resolvedPath = resolveAndValidatePath(filePath);
        if (!Files.exists(resolvedPath)) {
            throw new EntityNotFoundException("File not found: " + filePath);
        }
        try {
            Files.delete(resolvedPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete file: " + filePath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Validates the magic bytes of the file content against the expected extension.
     * This prevents content-type spoofing where a file with the wrong content
     * is given an image extension.
     *
     * @param data      the raw file bytes
     * @param extension the lowercased file extension (e.g. "png", "jpg")
     * @return true if the magic bytes match the extension, or if the extension is unknown
     */
    private boolean hasValidMagicBytes(byte[] data, String extension) {
        if (data == null || data.length < 4) return false;
        return switch (extension) {
            case "png" -> data[0] == (byte) 0x89 && data[1] == 0x50
                       && data[2] == 0x4E && data[3] == 0x47;
            case "jpg", "jpeg" -> data[0] == (byte) 0xFF && data[1] == (byte) 0xD8
                               && data[2] == (byte) 0xFF;
            default -> true; // unknown extension — allow
        };
    }

    private Path resolveAndValidatePath(String filePath) {
        if (filePath == null || filePath.contains("..")) {
            log.warn("Path traversal attempt blocked: {}", filePath);
            throw new BusinessRuleException("Invalid file path: path traversal is not allowed");
        }

        String rootPath = reclaimProperties.getStorage().getRootPath();
        Path rootNormalized = Paths.get(rootPath).toAbsolutePath().normalize();
        Path resolved = rootNormalized.resolve(filePath).normalize();

        if (!resolved.startsWith(rootNormalized)) {
            throw new BusinessRuleException("Invalid file path: access outside storage root is not allowed");
        }

        return resolved;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1);
    }
}
