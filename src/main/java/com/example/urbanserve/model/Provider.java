package com.example.urbanserve.model;

import jakarta.persistence.*;

@Entity
public class Provider {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String phone;
    private String city;
    private double rating;
    @ManyToOne(optional = false)
    private ServiceCategory category;

    public Provider() {}
    public Provider(String name, String phone, String city, double rating, ServiceCategory category) {
        this.name = name; this.phone = phone; this.city = city; this.rating = rating; this.category = category;
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getCity() { return city; }
    public double getRating() { return rating; }
    public ServiceCategory getCategory() { return category; }
}
