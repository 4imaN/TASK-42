package com.reclaim.portal.catalog.repository;

import com.reclaim.portal.catalog.entity.RecyclingItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface RecyclingItemRepository extends JpaRepository<RecyclingItem, Long> {

    List<RecyclingItem> findByActiveTrue();

    @Query("SELECT r FROM RecyclingItem r WHERE r.active = true " +
           "AND (:keyword IS NULL OR LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "     OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:category IS NULL OR r.category = :category) " +
           "AND (:condition IS NULL OR r.itemCondition = :condition) " +
           "AND (:minPrice IS NULL OR r.price >= :minPrice) " +
           "AND (:maxPrice IS NULL OR r.price <= :maxPrice)")
    List<RecyclingItem> searchItems(@Param("keyword") String keyword,
                                    @Param("category") String category,
                                    @Param("condition") String condition,
                                    @Param("minPrice") BigDecimal minPrice,
                                    @Param("maxPrice") BigDecimal maxPrice);

    @Query(value = "SELECT * FROM recycling_items r WHERE r.active = true " +
        "AND (:keyword IS NULL OR MATCH(r.title, r.description) AGAINST(:keyword IN BOOLEAN MODE)) " +
        "AND (:category IS NULL OR r.category = :category) " +
        "AND (:cond IS NULL OR r.item_condition = :cond) " +
        "AND (:minPrice IS NULL OR r.price >= :minPrice) " +
        "AND (:maxPrice IS NULL OR r.price <= :maxPrice)",
        nativeQuery = true)
    List<RecyclingItem> searchItemsFullText(@Param("keyword") String keyword,
        @Param("category") String category, @Param("cond") String condition,
        @Param("minPrice") BigDecimal minPrice, @Param("maxPrice") BigDecimal maxPrice);

    List<RecyclingItem> findByNormalizedTitle(String normalizedTitle);

    @Query("SELECT DISTINCT r.category FROM RecyclingItem r WHERE r.category IS NOT NULL ORDER BY r.category")
    List<String> findDistinctCategories();
}
