package com.reclaim.portal.appeals.repository;

import com.reclaim.portal.appeals.entity.Appeal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AppealRepository extends JpaRepository<Appeal, Long> {

    List<Appeal> findByAppellantId(Long appellantId);

    List<Appeal> findByOrderId(Long orderId);

    List<Appeal> findByAppealStatus(String appealStatus);
}
