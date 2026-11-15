package com.example.urbanserve.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev only: no dispatch service connected. Messages are logged and bookings stay PENDING. */
@Component
@ConditionalOnProperty(name = "app.dispatch.transport", havingValue = "none", matchIfMissing = true)
public class LoggingDispatchTransport implements DispatchTransport {
    private static final Logger log = LoggerFactory.getLogger(LoggingDispatchTransport.class);

    public LoggingDispatchTransport() {
        log.warn("Dispatch transport = none: bookings are not sent to urbanserve-dispatch and stay PENDING. "
                + "Set APP_DISPATCH_TRANSPORT=sqs to connect (see README).");
    }

    @Override
    public void send(String topic, String key, String payload) {
        log.info("[DISPATCH OFF] {} key={} {}", topic, key, payload);
    }
}
