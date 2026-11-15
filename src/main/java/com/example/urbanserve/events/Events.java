package com.example.urbanserve.events;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Event contracts (JSON). Keep in sync with urbanserve-dispatch com.example.dispatch.events.Events.
 * Additive changes only (new optional fields); never rename or remove a field.
 */
public final class Events {
    private Events() {}

    /** booking-service -> dispatch: please find a professional. */
    public record BookingRequested(
            String eventId,
            long bookingId,
            long categoryId,
            String city,
            String area,              // free-text address; dispatch may use it for routing later
            LocalDateTime scheduledAt,
            Long preferredProviderId,
            LocalDateTime occurredAt) {
        public static BookingRequested of(long bookingId, long categoryId, String city, String area,
                                          LocalDateTime scheduledAt, Long preferredProviderId) {
            return new BookingRequested(UUID.randomUUID().toString(), bookingId, categoryId, city, area,
                    scheduledAt, preferredProviderId, LocalDateTime.now());
        }
    }

    /** booking-service -> dispatch: free the professional's slot. */
    public record BookingCancelled(String eventId, long bookingId, LocalDateTime occurredAt) {
        public static BookingCancelled of(long bookingId) {
            return new BookingCancelled(UUID.randomUUID().toString(), bookingId, LocalDateTime.now());
        }
    }

    public enum Outcome { ASSIGNED, NO_PROVIDER }

    public record AssignedProvider(long id, String name, String phone, double rating) {}

    /** dispatch -> booking-service: result of the match. */
    public record AssignmentResult(
            String eventId,
            long bookingId,
            Outcome outcome,
            AssignedProvider provider,          // null when NO_PROVIDER
            LocalDateTime arrivalWindowStart,
            LocalDateTime arrivalWindowEnd,
            String reason,                      // set when NO_PROVIDER
            LocalDateTime occurredAt) {}
}
