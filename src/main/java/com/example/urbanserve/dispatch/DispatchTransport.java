package com.example.urbanserve.dispatch;

/**
 * Delivers booking messages to urbanserve-dispatch. Implementations also start listening for
 * dispatch's replies (booking.assignment) and hand them to {@link AssignmentHandler}.
 *   app.dispatch.transport=sqs  -> {@link SqsDispatchTransport} (AWS SQS, or ElasticMQ locally)
 *   app.dispatch.transport=none -> {@link LoggingDispatchTransport} (dev without dispatch: bookings stay PENDING)
 */
public interface DispatchTransport {
    /** Sends one message; throws if it could not be delivered (the outbox retries it). */
    void send(String topic, String key, String payload);
}
