package com.reclaim.portal.auth.repository;

import com.reclaim.portal.auth.entity.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    long countByUsernameAndSuccessFalseAndAttemptedAtAfter(String username, LocalDateTime after);
}
