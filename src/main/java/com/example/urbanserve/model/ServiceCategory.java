package com.example.urbanserve.model;

import jakarta.persistence.*;

@Entity
public class ServiceCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String description;
    private double basePrice;

    public ServiceCategory() {}
    public ServiceCategory(String name, String description, double basePrice) {
        this.name = name; this.description = description; this.basePrice = basePrice;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public double getBasePrice() { return basePrice; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
}
