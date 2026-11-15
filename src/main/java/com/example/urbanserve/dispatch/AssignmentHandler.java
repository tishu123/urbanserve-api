package com.example.urbanserve.dispatch;

import com.example.urbanserve.events.Events.AssignmentResult;
import com.example.urbanserve.events.Events.Outcome;
import com.example.urbanserve.model.Booking;
import com.example.urbanserve.model.BookingStatus;
import com.example.urbanserve.repo.BookingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies dispatch's reply (booking.assignment) to a booking. Idempotent: replies are applied only
 * to PENDING bookings, so redelivered or duplicate messages change nothing.
 *   ASSIGNED    -> CONFIRMED with the professional and arrival window
 *   NO_PROVIDER -> CANCELLED with dispatch's reason
 * If the customer cancelled while dispatch was still matching, an ASSIGNED reply re-sends
 * booking.cancelled so dispatch frees the slot (the two queues aren't ordered relative to each other).
 */
@Service
public class AssignmentHandler {
    private static final Logger log = LoggerFactory.getLogger(AssignmentHandler.class);

    private final BookingRepository bookings;
    private final DispatchOutbox outbox;
    private final ObjectMapper json;

    public AssignmentHandler(BookingRepository bookings, DispatchOutbox outbox, ObjectMapper json) {
        this.bookings = bookings;
        this.outbox = outbox;
        this.json = json;
    }

    /** Throws on unreadable JSON or a DB conflict, so the message is redelivered (then dead-lettered). */
    @Transactional
    public void apply(String payload) {
        AssignmentResult r;
        try {
            r = json.readValue(payload, AssignmentResult.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad booking.assignment message: " + e.getMessage(), e);
        }

        Booking b = bookings.findById(r.bookingId()).orElse(null);
        if (b == null) {
            log.warn("Assignment for unknown booking #{} ignored", r.bookingId());
            return;
        }

        if (b.getStatus() == BookingStatus.PENDING) {
            if (r.outcome() == Outcome.ASSIGNED && r.provider() != null) {
                var p = r.provider();
                b.confirm(new Booking.AssignedProvider(p.id(), p.name(), p.phone(), p.rating()),
                        r.arrivalWindowStart(), r.arrivalWindowEnd());
                log.info("Booking #{} CONFIRMED: provider #{} {}, arriving {}-{}",
                        b.getId(), p.id(), p.name(), r.arrivalWindowStart(), r.arrivalWindowEnd());
            } else {
                String reason = r.reason() == null || r.reason().isBlank()
                        ? "No professional is free at the chosen time" : r.reason();
                b.cancel(reason);
                log.info("Booking #{} not assigned: {}", b.getId(), reason);
            }
            bookings.save(b);
        } else if (b.getStatus() == BookingStatus.CANCELLED && r.outcome() == Outcome.ASSIGNED
                && b.getAssignedProvider() == null) {
            log.info("Booking #{} was cancelled before dispatch assigned it; asking dispatch to free the slot", b.getId());
            outbox.bookingCancelled(b.getId());
        }
        // Any other state (already CONFIRMED, completed, ...): duplicate reply, nothing to do.
    }
}
