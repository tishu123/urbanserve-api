package com.example.urbanserve.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;

/**
 * A booking starts PENDING while Dispatch looks for a professional.
 * Dispatch can confirm it with an arrival window or cancel it with a reason.
 */
@Entity
public class Booking {

    /** Snapshot of the professional assigned by Dispatch. */
    public record AssignedProvider(Long id, String name, String phone, Double rating) {}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    /** The customer's preference, not necessarily the assigned professional. */
    @ManyToOne
    @JoinColumn(name = "provider_id")
    private Provider provider;

    private String city;
    private Long assignedProviderId;
    private String assignedProviderName;
    private String assignedProviderPhone;
    private Double assignedProviderRating;
    private LocalDateTime arrivalWindowStart;
    private LocalDateTime arrivalWindowEnd;
    private String statusReason;

    @Column(name = "total_amount_paise")
    private Long totalAmountPaise;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "booking_item",
            joinColumns = @JoinColumn(name = "booking_id")
    )
    @OrderBy("packageCode ASC")
    private List<BookingItem> items = new ArrayList<>();

    @Version
    @JsonIgnore
    private long version;

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getAddress() { return address; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public BookingStatus getStatus() { return status; }
    public ServiceCategory getCategory() { return category; }

    @JsonIgnore
    public Provider getProvider() { return provider; }

    public Long getPreferredProviderId() {
        return provider == null ? null : provider.getId();
    }

    public String getCity() { return city; }

    public AssignedProvider getAssignedProvider() {
        return assignedProviderId == null ? null
                : new AssignedProvider(
                        assignedProviderId,
                        assignedProviderName,
                        assignedProviderPhone,
                        assignedProviderRating
                );
    }

    public LocalDateTime getArrivalWindowStart() { return arrivalWindowStart; }
    public LocalDateTime getArrivalWindowEnd() { return arrivalWindowEnd; }
    public String getStatusReason() { return statusReason; }
    public Long getTotalAmountPaise() { return totalAmountPaise; }
    public List<BookingItem> getItems() { return List.copyOf(items); }

    public void setCustomerName(String v) { this.customerName = v; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
    public void setAddress(String v) { this.address = v; }
    public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public void setCategory(ServiceCategory v) { this.category = v; }
    public void setProvider(Provider v) { this.provider = v; }
    public void setCity(String v) { this.city = v; }

    /** Save the package names and prices as they were when the booking was made. */
    public void setPriceSnapshot(List<BookingItem> pricedItems, long totalAmountPaise) {
        this.items.clear();
        this.items.addAll(pricedItems);
        this.totalAmountPaise = totalAmountPaise;
    }

    public void confirm(AssignedProvider p, LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.assignedProviderId = p.id();
        this.assignedProviderName = p.name();
        this.assignedProviderPhone = p.phone();
        this.assignedProviderRating = p.rating();
        this.arrivalWindowStart = windowStart;
        this.arrivalWindowEnd = windowEnd;
        this.statusReason = null;
        this.status = BookingStatus.CONFIRMED;
    }

    public void cancel(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.statusReason = reason;
    }
}