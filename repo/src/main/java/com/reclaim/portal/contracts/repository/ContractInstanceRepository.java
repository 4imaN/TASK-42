package com.reclaim.portal.contracts.repository;

import com.reclaim.portal.contracts.entity.ContractInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractInstanceRepository extends JpaRepository<ContractInstance, Long> {

    List<ContractInstance> findByUserId(Long userId);

    Optional<ContractInstance> findByOrderId(Long orderId);

    List<ContractInstance> findByContractStatus(String contractStatus);

    List<ContractInstance> findByEndDateBeforeAndContractStatusIn(LocalDate date, List<String> statuses);
}
