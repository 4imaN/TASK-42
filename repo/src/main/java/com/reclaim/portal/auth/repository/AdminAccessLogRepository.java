package com.reclaim.portal.auth.repository;

import com.reclaim.portal.auth.entity.AdminAccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminAccessLogRepository extends JpaRepository<AdminAccessLog, Long> {

    List<AdminAccessLog> findByAdminUserIdOrderByCreatedAtDesc(Long adminUserId);
}
