package com.reclaim.portal.appeals.service;

import com.reclaim.portal.appeals.entity.Appeal;
import com.reclaim.portal.appeals.entity.ArbitrationOutcome;
import com.reclaim.portal.appeals.entity.EvidenceFile;
import com.reclaim.portal.appeals.repository.AppealRepository;
import com.reclaim.portal.appeals.repository.ArbitrationOutcomeRepository;
import com.reclaim.portal.appeals.repository.EvidenceFileRepository;
import com.reclaim.portal.common.exception.BusinessRuleException;
import com.reclaim.portal.common.exception.EntityNotFoundException;
import com.reclaim.portal.contracts.repository.ContractInstanceRepository;
import com.reclaim.portal.orders.entity.Order;
import com.reclaim.portal.orders.repository.OrderRepository;
import com.reclaim.portal.storage.dto.StorageResult;
import com.reclaim.portal.storage.service.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class AppealService {

    private static final String STATUS_OPEN     = "OPEN";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String ENTITY_TYPE_APPEAL = "APPEAL";

    private final AppealRepository appealRepository;
    private final ArbitrationOutcomeRepository outcomeRepository;
    private final EvidenceFileRepository evidenceFileRepository;
    private final OrderRepository orderRepository;
    private final ContractInstanceRepository contractInstanceRepository;
    private final StorageService storageService;

    public AppealService(AppealRepository appealRepository,
                         ArbitrationOutcomeRepository outcomeRepository,
                         EvidenceFileRepository evidenceFileRepository,
                         OrderRepository orderRepository,
                         ContractInstanceRepository contractInstanceRepository,
                         StorageService storageService) {
        this.appealRepository = appealRepository;
        this.outcomeRepository = outcomeRepository;
        this.evidenceFileRepository = evidenceFileRepository;
        this.orderRepository = orderRepository;
        this.contractInstanceRepository = contractInstanceRepository;
        this.storageService = storageService;
    }

    /**
     * Authorization helper: verifies that the actor may access the given appeal.
     * Staff may always access. Otherwise actorId must be the appellant.
     */
    public void requireAppealAccess(Appeal appeal, Long actorId, boolean isStaff) {
        if (isStaff) {
            return;
        }
        if (actorId != null && actorId.equals(appeal.getAppellantId())) {
            return;
        }
        throw new BusinessRuleException("Access denied to appeal");
    }

    /**
     * Creates a new OPEN appeal linked to the given order (and optionally a contract).
     * When not staff, verifies that the appellant owns the referenced order.
     */
    public Appeal createAppeal(Long orderId, Long contractId, Long appellantId, String reason) {
        return createAppeal(orderId, contractId, appellantId, reason, true);
    }

    public Appeal createAppeal(Long orderId, Long contractId, Long appellantId, String reason,
                               boolean isStaff) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Order", orderId));

        if (!isStaff && (appellantId == null || !appellantId.equals(order.getUserId()))) {
            throw new BusinessRuleException("Access denied");
        }

        // Validate that the provided contractId belongs to this order
        if (contractId != null) {
            com.reclaim.portal.contracts.entity.ContractInstance contract =
                    contractInstanceRepository.findById(contractId).orElse(null);
            if (contract == null || !orderId.equals(contract.getOrderId())) {
                throw new BusinessRuleException("Contract does not belong to the referenced order");
            }
        }

        Appeal appeal = new Appeal();
        appeal.setOrderId(orderId);
        appeal.setContractId(contractId);
        appeal.setAppellantId(appellantId);
        appeal.setReason(reason);
        appeal.setAppealStatus(STATUS_OPEN);
        appeal.setCreatedAt(LocalDateTime.now());
        appeal.setUpdatedAt(LocalDateTime.now());
        return appealRepository.save(appeal);
    }

    /**
     * Stores a file as evidence for the given appeal.
     * The uploader must be the appellant or a staff member.
     */
    public EvidenceFile addEvidence(Long appealId, Long uploadedBy, MultipartFile file) {
        return addEvidence(appealId, uploadedBy, file, true);
    }

    public EvidenceFile addEvidence(Long appealId, Long uploadedBy, MultipartFile file,
                                    boolean isStaff) {
        Appeal appeal = loadAppeal(appealId);
        requireAppealAccess(appeal, uploadedBy, isStaff);

        StorageResult result = storageService.store(file, "evidence");

        EvidenceFile evidence = new EvidenceFile();
        evidence.setEntityType(ENTITY_TYPE_APPEAL);
        evidence.setEntityId(appeal.getId());
        evidence.setFileName(result.fileName());
        evidence.setFilePath(result.filePath());
        evidence.setContentType(result.contentType());
        evidence.setFileSize(result.fileSize());
        evidence.setChecksum(result.checksum());
        evidence.setUploadedBy(uploadedBy);
        evidence.setUploadedAt(LocalDateTime.now());
        return evidenceFileRepository.save(evidence);
    }

    /**
     * Resolves an open appeal, setting its status to RESOLVED and recording an
     * ArbitrationOutcome.
     */
    public Appeal resolveAppeal(Long appealId, Long decidedBy, String outcome, String reasoning) {
        Appeal appeal = loadAppeal(appealId);

        if (!STATUS_OPEN.equals(appeal.getAppealStatus())) {
            throw new BusinessRuleException(
                    "Appeal must be OPEN to resolve; was: " + appeal.getAppealStatus());
        }

        appeal.setAppealStatus(STATUS_RESOLVED);
        appeal.setUpdatedAt(LocalDateTime.now());
        appealRepository.save(appeal);

        ArbitrationOutcome arbitration = new ArbitrationOutcome();
        arbitration.setAppealId(appealId);
        arbitration.setDecidedBy(decidedBy);
        arbitration.setOutcome(outcome);
        arbitration.setReasoning(reasoning);
        arbitration.setDecidedAt(LocalDateTime.now());
        outcomeRepository.save(arbitration);

        return appeal;
    }

    /**
     * Returns a composite view of an appeal including its evidence files and outcome (if any).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAppealDetails(Long appealId) {
        Appeal appeal = loadAppeal(appealId);
        List<EvidenceFile> evidence =
                evidenceFileRepository.findByEntityTypeAndEntityId(ENTITY_TYPE_APPEAL, appealId);
        Optional<ArbitrationOutcome> outcomeOpt = outcomeRepository.findByAppealId(appealId);

        return Map.of(
                "appeal", appeal,
                "evidence", evidence,
                "outcome", outcomeOpt.orElse(null) != null ? outcomeOpt.get() : Map.of()
        );
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAppealDetails(Long appealId, Long actorId, boolean isStaff) {
        Appeal appeal = loadAppeal(appealId);
        requireAppealAccess(appeal, actorId, isStaff);
        List<EvidenceFile> evidence =
                evidenceFileRepository.findByEntityTypeAndEntityId(ENTITY_TYPE_APPEAL, appealId);
        Optional<ArbitrationOutcome> outcomeOpt = outcomeRepository.findByAppealId(appealId);

        return Map.of(
                "appeal", appeal,
                "evidence", evidence,
                "outcome", outcomeOpt.orElse(null) != null ? outcomeOpt.get() : Map.of()
        );
    }

    @Transactional(readOnly = true)
    public List<Appeal> getAppealsForUser(Long appellantId) {
        return appealRepository.findByAppellantId(appellantId);
    }

    @Transactional(readOnly = true)
    public Appeal getAppeal(Long appealId) {
        return loadAppeal(appealId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Appeal loadAppeal(Long id) {
        return appealRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Appeal", id));
    }
}
