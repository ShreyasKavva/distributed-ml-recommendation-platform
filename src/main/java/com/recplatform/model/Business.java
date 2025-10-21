package com.recplatform.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "businesses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Business {

    @Id
    @Column(name = "business_id", length = 64)
    private String id;

    @Column(nullable = false)
    private String name;

    private String address;
    private String city;
    private String state;

    @Column(name = "postal_code")
    private String postalCode;

    private Double latitude;
    private Double longitude;
    private Double stars;

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "is_open")
    private Boolean isOpen;

    @ElementCollection
    @CollectionTable(name = "business_categories", joinColumns = @JoinColumn(name = "business_id"))
    @Column(name = "category")
    private List<String> categories;
}
