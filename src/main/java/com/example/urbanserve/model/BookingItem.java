package com.example.urbanserve.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class BookingItem {

    @Column(name = "package_code", nullable = false, length = 80)
    private String packageCode;

    @Column(name = "package_name", nullable = false, length = 120)
    private String packageName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_amount_paise", nullable = false)
    private long unitAmountPaise;

    protected BookingItem() {
    }

    public BookingItem(String packageCode, String packageName,
                       int quantity, long unitAmountPaise) {
        this.packageCode = packageCode;
        this.packageName = packageName;
        this.quantity = quantity;
        this.unitAmountPaise = unitAmountPaise;
    }

    public String getPackageCode() { return packageCode; }
    public String getPackageName() { return packageName; }
    public int getQuantity() { return quantity; }
    public long getUnitAmountPaise() { return unitAmountPaise; }
}