package com.reclaim.portal.contracts.repository;

import com.reclaim.portal.contracts.entity.ContractTemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractTemplateVersionRepository extends JpaRepository<ContractTemplateVersion, Long> {

    List<ContractTemplateVersion> findByTemplateIdOrderByVersionNumberDesc(Long templateId);

    Optional<ContractTemplateVersion> findFirstByTemplateIdOrderByVersionNumberDesc(Long templateId);
}
