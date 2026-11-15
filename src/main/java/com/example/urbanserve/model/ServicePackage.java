
package com.example.urbanserve.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(
    name = "service_package",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_service_package_category_code",
            columnNames = {"category_id", "code"}
        )
    },
    indexes = {
        @Index(
            name = "idx_service_package_category_active",
            columnList = "category_id, active"
        )
    }
)
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Each package belongs to an existing service category.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    private String code;

    private String name;

    @Column(name = "price_factor", precision = 10, scale = 4, nullable = false)
    private BigDecimal priceFactor;

    @Column(nullable = false)
    private boolean active = true;

    // Required by JPA.
    protected ServicePackage() {
    }

    // Used when creating a new package.
    public ServicePackage(
            ServiceCategory category,
            String code,
            String name,
            BigDecimal priceFactor) {

        this.category = category;
        this.code = code;
        this.name = name;
        this.priceFactor = priceFactor;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    @JsonIgnore
    public ServiceCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPriceFactor() {
        return priceFactor;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
