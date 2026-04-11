package com.reclaim.portal.appeals.repository;

import com.reclaim.portal.appeals.entity.EvidenceFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceFileRepository extends JpaRepository<EvidenceFile, Long> {

    List<EvidenceFile> findByEntityTypeAndEntityId(String entityType, Long entityId);

    List<EvidenceFile> findByFilePath(String filePath);
}
