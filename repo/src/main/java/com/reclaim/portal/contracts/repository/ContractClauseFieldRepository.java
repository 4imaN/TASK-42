package com.reclaim.portal.contracts.repository;

import com.reclaim.portal.contracts.entity.ContractClauseField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractClauseFieldRepository extends JpaRepository<ContractClauseField, Long> {

    List<ContractClauseField> findByTemplateVersionIdOrderByDisplayOrderAsc(Long templateVersionId);
}
