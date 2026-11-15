package com.example.urbanserve.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** One OTP sent to a phone. Only a hash of the code is stored. Times are UTC. */
@Entity
public class OtpChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String phone;
    private String codeHash;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private int attempts;
    private boolean consumed;

    protected OtpChallenge() {}

    public OtpChallenge(String phone, String codeHash, Instant now, Instant expiresAt) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.createdAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        this.expiresAt = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public Instant getCreatedAt() { return createdAt.toInstant(ZoneOffset.UTC); }
    public Instant getExpiresAt() { return expiresAt.toInstant(ZoneOffset.UTC); }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public void incrementAttempts() { this.attempts++; }
    public void consume() { this.consumed = true; }
}
