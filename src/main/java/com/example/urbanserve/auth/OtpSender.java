package com.example.urbanserve.auth;

/** Delivers an OTP to a 10-digit Indian mobile number. */
public interface OtpSender {
    void send(String phone, String code);
}
