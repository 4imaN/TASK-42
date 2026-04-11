package com.reclaim.portal.contracts.service;

import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.appeals.repository.EvidenceFileRepository;
import com.reclaim.portal.common.config.ReclaimProperties;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.contracts.entity.*;
import com.reclaim.portal.contracts.repository.*;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.storage.dto.StorageResult;
import com.reclaim.portal.storage.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STATUS_INITIATED     = "INITIATED";
    private static final String STATUS_CONFIRMED     = "CONFIRMED";
    private static final String STATUS_SIGNED        = "SIGNED";
    private static final String STATUS_ARCHIVED      = "ARCHIVED";
    private static final String STATUS_TERMINATED    = "TERMINATED";
    private static final String STATUS_VOIDED        = "VOIDED";
    private static final String STATUS_RENEWED       = "RENEWED";
    private static final String STATUS_ACTIVE        = "ACTIVE";
    private static final String STATUS_EXPIRING_SOON = "EXPIRING_SOON";

    private final ContractTemplateRepository templateRepository;
    private final ContractTemplateVersionRepository versionRepository;
    private final ContractClauseFieldRepository clauseFieldRepository;
    private final ContractInstanceRepository instanceRepository;
    private final SignatureArtifactRepository signatureRepository;
    private final OrderRepository orderRepository;
    private final StorageService storageService;
    private final ReclaimProperties reclaimProperties;
    private final EvidenceFileRepository evidenceFileRepository;

    public ContractService(ContractTemplateRepository templateRepository,
                           ContractTemplateVersionRepository versionRepository,
                           ContractClauseFieldRepository clauseFieldRepository,
                           ContractInstanceRepository instanceRepository,
                           SignatureArtifactRepository signatureRepository,
                           OrderRepository orderRepository,
                           StorageService storageService,
                           ReclaimProperties reclaimProperties,
                           EvidenceFileRepository evidenceFileRepository) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.clauseFieldRepository = clauseFieldRepository;
        this.instanceRepository = instanceRepository;
        this.signatureRepository = signatureRepository;
        this.orderRepository = orderRepository;
        this.storageService = storageService;
        this.reclaimProperties = reclaimProperties;
        this.evidenceFileRepository = evidenceFileRepository;
    }

    // =========================================================================
    // Template management
    // =========================================================================

    public ContractTemplate createTemplate(String name, String description, Long createdBy) {
        ContractTemplate template = new ContractTemplate();
        template.setName(name);
        template.setDescription(description);
        template.setActive(true);
        template.setCreatedBy(createdBy);
        template.setCreatedAt(LocalDateTime.now());
        template.setUpdatedAt(LocalDateTime.now());
        return templateRepository.save(template);
    }

    public ContractTemplateVersion createTemplateVersion(Long templateId, String content,
                                                         String changeNotes, Long createdBy) {
        ContractTemplate template = templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("ContractTemplate", templateId));

        int nextVersion = versionRepository
                .findFirstByTemplateIdOrderByVersionNumberDesc(templateId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        ContractTemplateVersion version = new ContractTemplateVersion();
        version.setTemplateId(template.getId());
        version.setVersionNumber(nextVersion);
        version.setContent(content);
        version.setChangeNotes(changeNotes);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        return versionRepository.save(version);
    }

    public ContractClauseField addClauseField(Long templateVersionId, String fieldName,
                                              String fieldType, String fieldLabel,
                                              boolean required, String defaultValue,
                                              int displayOrder) {
        versionRepository.findById(templateVersionId)
                .orElseThrow(() -> new EntityNotFoundException("ContractTemplateVersion", templateVersionId));

        ContractClauseField field = new ContractClauseField();
        field.setTemplateVersionId(templateVersionId);
        field.setFieldName(fieldName);
        field.setFieldType(fieldType);
        field.setFieldLabel(fieldLabel);
        field.setRequired(required);
        field.setDefaultValue(defaultValue);
        field.setDisplayOrder(displayOrder);
        return clauseFieldRepository.save(field);
    }

    @Transactional(readOnly = true)
    public List<ContractTemplate> getActiveTemplates() {
        return templateRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<ContractTemplateVersion> getTemplateVersions(Long templateId) {
        return versionRepository.findByTemplateIdOrderByVersionNumberDesc(templateId);
    }

    @Transactional(readOnly = true)
    public List<ContractClauseField> getClauseFields(Long templateVersionId) {
        return clauseFieldRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(templateVersionId);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    public ContractInstance initiateContract(Long orderId, Long templateVersionId, Long userId,
                                             Long reviewerId, String fieldValues,
                                             LocalDate startDate, LocalDate endDate) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));
        if (!"ACCEPTED".equals(order.getOrderStatus())) {
            throw new BusinessRuleException(
                "Contract can only be initiated for ACCEPTED orders; current status: " + order.getOrderStatus());
        }
        // The contract user is the order owner, not necessarily the caller
        Long contractUserId = order.getUserId();

        ContractTemplateVersion version = versionRepository.findById(templateVersionId)
                .orElseThrow(() -> new EntityNotFoundException("ContractTemplateVersion", templateVersionId));

        List<ContractClauseField> fields =
                clauseFieldRepository.findByTemplateVersionIdOrderByDisplayOrderAsc(templateVersionId);

        // Parse fieldValues as simple key=value pairs (JSON-like map usage via Jackson not required here)
        String rendered = renderContent(version.getContent(), fieldValues, fields);

        ContractInstance instance = new ContractInstance();
        instance.setOrderId(orderId);
        instance.setTemplateVersionId(templateVersionId);
        instance.setUserId(contractUserId);
        instance.setReviewerId(reviewerId);
        instance.setContractStatus(STATUS_INITIATED);
        instance.setRenderedContent(rendered);
        instance.setFieldValues(fieldValues);
        instance.setStartDate(startDate);
        instance.setEndDate(endDate);
        instance.setCreatedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        ContractInstance saved = instanceRepository.save(instance);
        log.info("Contract initiated: id={}, orderId={}, userId={}, reviewerId={}",
                saved.getId(), orderId, contractUserId, reviewerId);
        return saved;
    }

    /**
     * Authorization helper: verifies that the actor may access the given contract instance.
     * Staff may always access. Otherwise actorId must be the contract user or reviewer.
     */
    public void requireContractAccess(ContractInstance instance, Long actorId, boolean isStaff) {
        if (isStaff) {
            return;
        }
        if (actorId != null && actorId.equals(instance.getUserId())) {
            return;
        }
        if (actorId != null && actorId.equals(instance.getReviewerId())) {
            return;
        }
        throw new BusinessRuleException("Access denied to contract");
    }

    public ContractInstance confirmContract(Long instanceId, Long actorId) {
        return confirmContract(instanceId, actorId, true);
    }

    public ContractInstance confirmContract(Long instanceId, Long actorId, boolean isStaff) {
        ContractInstance instance = loadInstance(instanceId);
        requireContractAccess(instance, actorId, isStaff);
        requireStatus(instance, STATUS_INITIATED);
        instance.setContractStatus(STATUS_CONFIRMED);
        instance.setUpdatedAt(LocalDateTime.now());
        log.info("Contract confirmed: id={}, actorId={}", instanceId, actorId);
        return instanceRepository.save(instance);
    }

    public ContractInstance signContract(Long instanceId, Long signerId,
                                         String signatureType, byte[] sigData) {
        ContractInstance instance = loadInstance(instanceId);
        // Only the contract's user may sign
        if (signerId == null || !signerId.equals(instance.getUserId())) {
            throw new BusinessRuleException("Access denied to contract");
        }
        requireStatus(instance, STATUS_CONFIRMED);

        // Salt the signature hash for storage security
        String salt = java.util.UUID.randomUUID().toString();
        String encodedSig = java.util.Base64.getEncoder().encodeToString(sigData);
        String signatureHash = sha256Hex((salt + ":" + encodedSig).getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Wrap bytes as a MultipartFile for StorageService
        MultipartFile sigFile = new ByteArrayMultipartFile(
                "signature",
                "signature_" + signerId + ".png",
                "image/png",
                sigData
        );

        StorageResult result = storageService.store(sigFile, "signatures");

        SignatureArtifact artifact = new SignatureArtifact();
        artifact.setContractId(instanceId);
        artifact.setSignerId(signerId);
        artifact.setSignatureType(signatureType);
        artifact.setFilePath(result.filePath());
        artifact.setSignatureHash(salt + ":" + signatureHash);
        artifact.setChecksum(result.checksum());
        artifact.setSignedAt(LocalDateTime.now());
        signatureRepository.save(artifact);

        instance.setContractStatus(STATUS_SIGNED);
        instance.setSignedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        log.info("Contract signed: id={}, signerId={}", instanceId, signerId);
        return instanceRepository.save(instance);
    }

    public ContractInstance archiveContract(Long instanceId) {
        ContractInstance instance = loadInstance(instanceId);
        requireStatus(instance, STATUS_SIGNED);
        instance.setContractStatus(STATUS_ARCHIVED);
        instance.setArchivedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        log.info("Contract archived: id={}", instanceId);
        return instanceRepository.save(instance);
    }

    public ContractInstance terminateContract(Long instanceId) {
        ContractInstance instance = loadInstance(instanceId);
        String current = instance.getContractStatus();
        if (!STATUS_ACTIVE.equals(current) && !STATUS_SIGNED.equals(current)
                && !STATUS_RENEWED.equals(current)) {
            throw new BusinessRuleException(
                    "Contract must be ACTIVE, SIGNED, or RENEWED to terminate; was: " + current);
        }
        instance.setContractStatus(STATUS_TERMINATED);
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    public ContractInstance voidContract(Long instanceId) {
        ContractInstance instance = loadInstance(instanceId);
        if (STATUS_ARCHIVED.equals(instance.getContractStatus())) {
            throw new BusinessRuleException("Cannot void an ARCHIVED contract.");
        }
        instance.setContractStatus(STATUS_VOIDED);
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    public ContractInstance renewContract(Long instanceId, Long actorId, LocalDate newEndDate) {
        return renewContract(instanceId, actorId, newEndDate, true);
    }

    public ContractInstance renewContract(Long instanceId, Long actorId, LocalDate newEndDate,
                                          boolean isStaff) {
        ContractInstance instance = loadInstance(instanceId);
        requireContractAccess(instance, actorId, isStaff);
        String current = instance.getContractStatus();
        if (!STATUS_ACTIVE.equals(current) && !STATUS_SIGNED.equals(current)) {
            throw new BusinessRuleException(
                    "Contract must be ACTIVE or SIGNED to renew; was: " + current);
        }
        instance.setContractStatus(STATUS_RENEWED);
        instance.setEndDate(newEndDate);
        instance.setUpdatedAt(LocalDateTime.now());
        return instanceRepository.save(instance);
    }

    // =========================================================================
    // Status computation
    // =========================================================================

    /**
     * Computes the effective display status of a contract instance.
     * Terminal/explicit states are returned as-is; date-based transitions are applied
     * to SIGNED/ACTIVE/RENEWED contracts.
     */
    public String getContractStatus(ContractInstance instance) {
        String stored = instance.getContractStatus();

        if (STATUS_TERMINATED.equals(stored) || STATUS_VOIDED.equals(stored)
                || STATUS_ARCHIVED.equals(stored)) {
            return stored;
        }
        if (STATUS_RENEWED.equals(stored)) {
            return STATUS_RENEWED;
        }
        // Pre-signing statuses are returned as-is (INITIATED, CONFIRMED)
        if (STATUS_INITIATED.equals(stored) || STATUS_CONFIRMED.equals(stored)) {
            return stored;
        }

        // Date-based transitions apply only to SIGNED (post-signing) contracts
        LocalDate today = LocalDate.now();
        LocalDate endDate = instance.getEndDate();

        if (endDate != null) {
            if (endDate.isBefore(today)) {
                return STATUS_TERMINATED;
            }
            int expiringSoonDays = reclaimProperties.getContracts().getExpiringSoonDays();
            if (endDate.isBefore(today.plusDays(expiringSoonDays))) {
                return STATUS_EXPIRING_SOON;
            }
        }

        return STATUS_ACTIVE;
    }

    /**
     * Populates the transient {@code displayStatus} field on each contract instance
     * without mutating the persisted {@code contractStatus}. Safe for use in view layers.
     */
    public void populateDisplayStatus(List<ContractInstance> instances) {
        for (ContractInstance instance : instances) {
            instance.setDisplayStatus(getContractStatus(instance));
        }
    }

    /**
     * Populates the transient {@code displayStatus} field on a single contract instance.
     */
    public void populateDisplayStatus(ContractInstance instance) {
        instance.setDisplayStatus(getContractStatus(instance));
    }

    // =========================================================================
    // Query helpers
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ContractInstance> getUserContracts(Long userId) {
        return instanceRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public ContractInstance getContractDetail(Long instanceId) {
        return loadInstance(instanceId);
    }

    @Transactional(readOnly = true)
    public ContractInstance getContractDetail(Long instanceId, Long actorId, boolean isStaff) {
        ContractInstance instance = loadInstance(instanceId);
        requireContractAccess(instance, actorId, isStaff);
        return instance;
    }

    @Transactional(readOnly = true)
    public ContractInstance getPrintableContract(Long instanceId) {
        return loadInstance(instanceId);
    }

    @Transactional(readOnly = true)
    public ContractInstance getPrintableContract(Long instanceId, Long actorId, boolean isStaff) {
        ContractInstance instance = loadInstance(instanceId);
        requireContractAccess(instance, actorId, isStaff);
        return instance;
    }

    /**
     * Returns the signature artifact for a contract, if one exists.
     */
    @Transactional(readOnly = true)
    public SignatureArtifact getSignatureArtifact(Long contractId) {
        List<SignatureArtifact> artifacts = signatureRepository.findByContractId(contractId);
        return artifacts.isEmpty() ? null : artifacts.get(0);
    }

    // =========================================================================
    // Evidence
    // =========================================================================

    public EvidenceFile addContractEvidence(Long contractId, Long actorId, boolean isStaff, MultipartFile file) {
        ContractInstance instance = loadInstance(contractId);
        requireContractAccess(instance, actorId, isStaff);
        StorageResult result = storageService.store(file, "evidence");
        EvidenceFile evidence = new EvidenceFile();
        evidence.setEntityType("CONTRACT");
        evidence.setEntityId(contractId);
        evidence.setFileName(result.fileName());
        evidence.setFilePath(result.filePath());
        evidence.setContentType(result.contentType());
        evidence.setFileSize(result.fileSize());
        evidence.setChecksum(result.checksum());
        evidence.setUploadedBy(actorId);
        evidence.setUploadedAt(LocalDateTime.now());
        return evidenceFileRepository.save(evidence);
    }

    @Transactional(readOnly = true)
    public List<EvidenceFile> getContractEvidence(Long contractId, Long actorId, boolean isStaff) {
        ContractInstance instance = loadInstance(contractId);
        requireContractAccess(instance, actorId, isStaff);
        return evidenceFileRepository.findByEntityTypeAndEntityId("CONTRACT", contractId);
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private ContractInstance loadInstance(Long id) {
        return instanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("ContractInstance", id));
    }

    private void requireStatus(ContractInstance instance, String required) {
        if (!required.equals(instance.getContractStatus())) {
            throw new BusinessRuleException(
                    "Expected contract status " + required + " but was " + instance.getContractStatus());
        }
    }

    /**
     * Replaces {{fieldName}} placeholders in the template content with values from
     * the provided fieldValues string. Accepts JSON object format or legacy key=value pairs.
     * Validates that all required clause fields have non-blank values.
     */
    public String renderContent(String templateContent, String fieldValuesRaw,
                                 List<ContractClauseField> fields) {
        if (templateContent == null) return "";

        Map<String, String> values = parseFieldValues(fieldValuesRaw);

        // Always validate required fields — a missing required field is invalid regardless
        // of whether the caller passed an explicit fieldValues string.
        for (ContractClauseField field : fields) {
            if (field.isRequired()) {
                String value = values.get(field.getFieldName());
                if ((value == null || value.isBlank())
                        && (field.getDefaultValue() == null || field.getDefaultValue().isBlank())) {
                    throw new BusinessRuleException(
                            "Required clause field '" + field.getFieldName() + "' is missing");
                }
            }
        }

        String rendered = templateContent;
        for (ContractClauseField field : fields) {
            String placeholder = "{{" + field.getFieldName() + "}}";
            String value = values.getOrDefault(field.getFieldName(),
                    field.getDefaultValue() != null ? field.getDefaultValue() : "");
            // HTML-escape user-supplied values to prevent stored XSS
            value = value.replace("&", "&amp;")
                         .replace("<", "&lt;")
                         .replace(">", "&gt;")
                         .replace("\"", "&quot;")
                         .replace("'", "&#39;");
            rendered = rendered.replace(placeholder, value);
        }
        return rendered;
    }

    /**
     * Parses field values from either JSON object format (preferred) or legacy key=value format.
     * JSON format handles values containing commas, colons, quotes, etc. safely.
     */
    public Map<String, String> parseFieldValues(String fieldValuesRaw) {
        if (fieldValuesRaw == null || fieldValuesRaw.isBlank()) {
            return new LinkedHashMap<>();
        }

        String trimmed = fieldValuesRaw.trim();

        // Try JSON parsing first (preferred format)
        if (trimmed.startsWith("{")) {
            try {
                Map<String, String> result = objectMapper.readValue(trimmed,
                        new TypeReference<LinkedHashMap<String, String>>() {});
                return result;
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse field values as JSON, falling back to legacy format: {}",
                        e.getMessage());
            }
        }

        // Legacy key=value format (only for simple values without commas/colons)
        Map<String, String> map = new LinkedHashMap<>();
        for (String pair : trimmed.split(",")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // =========================================================================
    // Inner utility: lightweight MultipartFile backed by a byte array
    // =========================================================================

    /**
     * A minimal {@link MultipartFile} implementation backed by an in-memory byte array.
     * Used internally to wrap raw signature bytes before passing to StorageService.
     */
    private static final class ByteArrayMultipartFile implements MultipartFile {

        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        ByteArrayMultipartFile(String name, String originalFilename,
                               String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override public String getName()             { return name; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType()      { return contentType; }
        @Override public boolean isEmpty()            { return content.length == 0; }
        @Override public long getSize()               { return content.length; }
        @Override public byte[] getBytes()            { return content; }

        @Override
        public java.io.InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws java.io.IOException {
            java.nio.file.Files.write(dest.toPath(), content);
        }
    }
}
