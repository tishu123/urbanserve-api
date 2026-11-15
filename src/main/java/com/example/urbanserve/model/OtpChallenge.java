package com.example.urbanserve.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One OTP sent to a phone. Times are UTC.
 * Either codeHash (app-generated code; only an HMAC is stored) or providerRef (code generated and
 * checked by a managed provider such as Message Central VerifyNow) is set.
 */
@Entity
public class OtpChallenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String phone;
    private String codeHash;
    private String providerRef;
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

    /** A challenge whose code lives at a managed OTP provider. */
    public static OtpChallenge managed(String phone, String providerRef, Instant now, Instant expiresAt) {
        OtpChallenge c = new OtpChallenge(phone, null, now, expiresAt);
        c.providerRef = providerRef;
        return c;
    }

    public Long getId() { return id; }
    public String getPhone() { return phone; }
    public String getCodeHash() { return codeHash; }
    public String getProviderRef() { return providerRef; }
    public Instant getCreatedAt() { return createdAt.toInstant(ZoneOffset.UTC); }
    public Instant getExpiresAt() { return expiresAt.toInstant(ZoneOffset.UTC); }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public void incrementAttempts() { this.attempts++; }
    public void consume() { this.consumed = true; }
}
