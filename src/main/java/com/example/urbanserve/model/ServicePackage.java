package com.example.urbanserve.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "service_package",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_service_package_category_code",
                columnNames = {"category_id", "code"}
        ),
        indexes = @Index(
                name = "idx_service_package_category_active",
                columnList = "category_id, active"
        )
)
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "price_factor", precision = 10, scale = 4, nullable = false)
    private BigDecimal priceFactor;

    @Column(nullable = false)
    private boolean active = true;

    protected ServicePackage() {
    }

    public ServicePackage(ServiceCategory category, String code,
                          String name, BigDecimal priceFactor) {
        this.category = category;
        this.code = code;
        this.name = name;
        this.priceFactor = priceFactor;
    }

    public Long getId() { return id; }

    @JsonIgnore
    public ServiceCategory getCategory() { return category; }

    public String getCode() { return code; }
    public String getName() { return name; }
    public BigDecimal getPriceFactor() { return priceFactor; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public BigDecimal priceInRupees(double categoryBasePrice) {
        return BigDecimal.valueOf(categoryBasePrice)
                .multiply(priceFactor)
                .divide(BigDecimal.TEN, 0, RoundingMode.HALF_UP)
                .multiply(BigDecimal.TEN)
                .subtract(BigDecimal.ONE)
                .max(BigDecimal.valueOf(99));
    }
}