package com.recplatform.repository;

import com.recplatform.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, String> {

    List<Review> findByUserId(String userId);

    @Query("SELECT r.businessId FROM Review r WHERE r.userId = :userId AND r.stars >= :minStars")
    List<String> findLikedBusinessIds(@Param("userId") String userId, @Param("minStars") Double minStars);

    /**
     * Item-item collaborative signal: for a given business, find other businesses
     * that users who rated it highly (>= 4 stars) also rated highly, ranked by
     * how often that co-occurrence happens. This is the backbone of the
     * "collaborative" half of the hybrid recommender.
     */
    @Query(value = """
        SELECT r2.business_id AS business_id, COUNT(*) AS co_occurrence
        FROM reviews r1
        JOIN reviews r2 ON r1.user_id = r2.user_id AND r1.business_id != r2.business_id
        WHERE r1.business_id = :businessId AND r1.stars >= 4 AND r2.stars >= 4
        GROUP BY r2.business_id
        ORDER BY co_occurrence DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findCoOccurringBusinesses(@Param("businessId") String businessId, @Param("limit") int limit);
}
