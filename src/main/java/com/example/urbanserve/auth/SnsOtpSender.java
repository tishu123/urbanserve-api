package com.example.urbanserve.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import jakarta.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;

/**
 * Sends the OTP as a transactional SMS through Amazon SNS.
 * Credentials and region come from the EC2 instance role / AWS_REGION.
 */
@Component
@ConditionalOnProperty(name = "app.otp.sender", havingValue = "sns")
public class SnsOtpSender implements OtpSender {

    private final SnsClient sns = SnsClient.create();
    private final String message;
    private final String senderId;
    private final String entityId;
    private final String templateId;

    public SnsOtpSender(@Value("${app.otp.message}") String message,
                        @Value("${app.otp.sns.sender-id:}") String senderId,
                        @Value("${app.otp.sns.entity-id:}") String entityId,
                        @Value("${app.otp.sns.template-id:}") String templateId) {
        this.message = message;
        this.senderId = senderId;
        this.entityId = entityId;
        this.templateId = templateId;
    }

    @Override
    public void send(String phone, String code) {
        Map<String, MessageAttributeValue> attrs = new HashMap<>();
        attrs.put("AWS.SNS.SMS.SMSType", str("Transactional"));
        if (!senderId.isBlank()) attrs.put("AWS.SNS.SMS.SenderID", str(senderId));
        // India DLT (TRAI) registration IDs
        if (!entityId.isBlank()) attrs.put("AWS.MM.SMS.EntityId", str(entityId));
        if (!templateId.isBlank()) attrs.put("AWS.MM.SMS.TemplateId", str(templateId));

        sns.publish(PublishRequest.builder()
                .phoneNumber("+91" + phone)
                .message(String.format(message, code))
                .messageAttributes(attrs)
                .build());
    }

    private static MessageAttributeValue str(String v) {
        return MessageAttributeValue.builder().dataType("String").stringValue(v).build();
    }

    @PreDestroy
    void close() { sns.close(); }
}
