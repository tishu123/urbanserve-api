package com.example.urbanserve.events;

/** Message types shared with urbanserve-dispatch (on SQS each has its own queue: booking-requested, ...). Keys are the booking id. */
public final class Topics {
    public static final String BOOKING_REQUESTED = "booking.requested";
    public static final String BOOKING_CANCELLED = "booking.cancelled";
    public static final String BOOKING_ASSIGNMENT = "booking.assignment";
    private Topics() {}
}
