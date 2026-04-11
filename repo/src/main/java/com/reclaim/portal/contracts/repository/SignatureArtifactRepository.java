package com.reclaim.portal.contracts.repository;

import com.reclaim.portal.contracts.entity.SignatureArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SignatureArtifactRepository extends JpaRepository<SignatureArtifact, Long> {

    List<SignatureArtifact> findByContractId(Long contractId);

    List<SignatureArtifact> findByFilePath(String filePath);
}
