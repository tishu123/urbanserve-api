package com.example.urbanserve.dispatch;

import com.example.urbanserve.events.Events.BookingCancelled;
import com.example.urbanserve.events.Events.BookingRequested;
import com.example.urbanserve.events.Topics;
import com.example.urbanserve.model.Booking;
import com.example.urbanserve.model.OutboxEvent;
import com.example.urbanserve.repo.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queues messages for urbanserve-dispatch inside the caller's transaction (MANDATORY), so the
 * booking change and its message are committed together. OutboxRelay sends them afterwards.
 */
@Service
public class DispatchOutbox {
    private final OutboxRepository outbox;
    private final ObjectMapper json;

    public DispatchOutbox(OutboxRepository outbox, ObjectMapper json) {
        this.outbox = outbox;
        this.json = json;
    }

    /** Ask dispatch to find a professional for a new booking. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingRequested(Booking b) {
        enqueue(Topics.BOOKING_REQUESTED, b.getId(), BookingRequested.of(
                b.getId(), b.getCategory().getId(), b.getCity(), b.getAddress(),
                b.getScheduledAt(), b.getPreferredProviderId()));
    }

    /** Tell dispatch to free the professional's slot. Safe to send more than once. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void bookingCancelled(long bookingId) {
        enqueue(Topics.BOOKING_CANCELLED, bookingId, BookingCancelled.of(bookingId));
    }

    private void enqueue(String topic, long bookingId, Object event) {
        try {
            outbox.save(new OutboxEvent(topic, Long.toString(bookingId), json.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize " + topic, e);
        }
    }
}
