package com.example.urbanserve.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev only: writes the OTP to the server log instead of sending an SMS. */
@Component
@ConditionalOnProperty(name = "app.otp.sender", havingValue = "log", matchIfMissing = true)
public class LoggingOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingOtpSender.class);

    public LoggingOtpSender() {
        log.warn("OTP sender = log. OTP codes are printed to the log; never use this in production.");
    }

    @Override
    public void send(String phone, String code) {
        log.info("[DEV OTP] phone=******{} code={}", phone.substring(phone.length() - 4), code);
    }
}
