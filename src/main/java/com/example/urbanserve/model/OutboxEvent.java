package com.example.urbanserve.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** A message to urbanserve-dispatch, saved with the booking change and sent later by OutboxRelay. */
@Entity
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String topic;
    private String messageKey;
    @Column(columnDefinition = "text")
    private String payload;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private int attempts;
    private String lastError;

    protected OutboxEvent() {}

    public OutboxEvent(String topic, String messageKey, String payload) {
        this.topic = topic;
        this.messageKey = messageKey;
        this.payload = payload;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public LocalDateTime getSentAt() { return sentAt; }
    public int getAttempts() { return attempts; }

    public void markSent() { this.sentAt = LocalDateTime.now(); this.lastError = null; }

    public void markFailed(String error) {
        this.attempts++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
    }
}
