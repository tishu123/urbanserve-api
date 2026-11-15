package com.example.urbanserve.auth;

/**
 * An OTP service that generates, sends and checks the code itself (e.g. Message Central VerifyNow).
 * The app never sees the code; it keeps only the provider's reference for the latest challenge.
 *
 * Implementations throw {@link org.springframework.web.server.ResponseStatusException} for errors
 * the user should see (rate limits, provider outage).
 */
public interface ManagedOtpProvider {

    /** @param reference provider id for this verification (stored server-side, never sent to the client) */
    record Started(String reference, long resendAfterSeconds) {}

    enum Result { VALID, WRONG, EXPIRED }

    /** Asks the provider to generate a 6-digit code and send it to a 10-digit Indian mobile number. */
    Started start(String phone);

    /** Checks the code the user entered against the provider's verification. */
    Result check(String reference, String phone, String code);
}
