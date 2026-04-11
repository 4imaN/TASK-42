package com.reclaim.portal.orders.repository;

import com.reclaim.portal.orders.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    List<Order> findByOrderStatus(String orderStatus);

    List<Order> findByReviewerId(Long reviewerId);

    List<Order> findByOrderStatusIn(List<String> statuses);

    long countByOrderStatus(String orderStatus);
}
