package com.recplatform.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id", length = 64)
    private String id;

    private String name;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "average_stars")
    private Double averageStars;

    @Column(name = "yelping_since")
    private LocalDate yelpingSince;
}
