package com.example.urbanserve.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String customerName;
    private String customerPhone;
    private String address;
    private LocalDateTime scheduledAt;
    private LocalDateTime createdAt = LocalDateTime.now();
    @Enumerated(EnumType.STRING)
    private BookingStatus status = BookingStatus.PENDING;
    @ManyToOne(optional = false)
    private ServiceCategory category;
    @ManyToOne(optional = false)
    private Provider provider;

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getAddress() { return address; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BookingStatus getStatus() { return status; }
    public ServiceCategory getCategory() { return category; }
    public Provider getProvider() { return provider; }
    public void setCustomerName(String v) { this.customerName = v; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
    public void setAddress(String v) { this.address = v; }
    public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public void setCategory(ServiceCategory v) { this.category = v; }
    public void setProvider(Provider v) { this.provider = v; }
}
