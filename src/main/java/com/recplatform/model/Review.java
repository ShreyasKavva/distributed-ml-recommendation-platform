package com.recplatform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "reviews",
        indexes = {
                @Index(name = "idx_review_user_lookup", columnList = "user_id"),
                @Index(name = "idx_review_business_lookup", columnList = "business_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @Column(name = "review_id", length = 64)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false)
    private String userId;

    @Column(name = "business_id", length = 64, nullable = false)
    private String businessId;

    private Double stars;

    @Column(name = "review_date")
    private LocalDateTime date;

    @Column(name = "useful_count")
    private Integer usefulCount;
}
