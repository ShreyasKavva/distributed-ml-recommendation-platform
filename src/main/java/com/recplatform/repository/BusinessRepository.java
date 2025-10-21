package com.recplatform.repository;

import com.recplatform.model.Business;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BusinessRepository extends JpaRepository<Business, String> {

    @Query("SELECT DISTINCT b FROM Business b JOIN b.categories c WHERE c IN :categories AND b.isOpen = true")
    List<Business> findOpenBusinessesByCategories(@Param("categories") List<String> categories);

    List<Business> findTop50ByOrderByReviewCountDesc();
}
