package com.example.urbanserve.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A customer booking. Lifecycle with urbanserve-dispatch:
 *   PENDING (finding a professional) -> CONFIRMED (dispatch assigned one, arrival window set)
 *                                    -> CANCELLED (no professional free: statusReason says why, or the customer cancelled)
 */
@Entity
public class Booking {

    /** The professional dispatch assigned (snapshot of dispatch's roster at assignment time). */
    public record AssignedProvider(Long id, String name, String phone, Double rating) {}

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
    /** Customer's preferred professional (optional). The actual one is assignedProvider. */
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
    public Long getPreferredProviderId() { return provider == null ? null : provider.getId(); }
    public String getCity() { return city; }
    public AssignedProvider getAssignedProvider() {
        return assignedProviderId == null ? null
                : new AssignedProvider(assignedProviderId, assignedProviderName, assignedProviderPhone, assignedProviderRating);
    }
    public LocalDateTime getArrivalWindowStart() { return arrivalWindowStart; }
    public LocalDateTime getArrivalWindowEnd() { return arrivalWindowEnd; }
    public String getStatusReason() { return statusReason; }
    public void setCustomerName(String v) { this.customerName = v; }
    public void setCustomerPhone(String v) { this.customerPhone = v; }
    public void setAddress(String v) { this.address = v; }
    public void setScheduledAt(LocalDateTime v) { this.scheduledAt = v; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public void setCategory(ServiceCategory v) { this.category = v; }
    public void setProvider(Provider v) { this.provider = v; }
    public void setCity(String v) { this.city = v; }

    /** Dispatch found a professional. */
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

    /** Cancelled by the customer/admin (reason null) or because no professional was free. */
    public void cancel(String reason) {
        this.status = BookingStatus.CANCELLED;
        this.statusReason = reason;
    }
}
