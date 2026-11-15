package com.example.urbanserve.dispatch;

import com.example.urbanserve.events.Topics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;
import java.util.Map;

/**
 * app.dispatch.transport=sqs: sends booking-requested / booking-cancelled and consumes
 * booking-assignment. On AWS, credentials come from the EC2 instance role. Locally, set
 * app.dispatch.sqs.endpoint to ElasticMQ (http://localhost:9324); dummy credentials are used.
 */
@Component
@ConditionalOnProperty(name = "app.dispatch.transport", havingValue = "sqs")
public class SqsDispatchTransport implements DispatchTransport, SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(SqsDispatchTransport.class);

    private final SqsClient sqs;
    private final Map<String, String> queueByTopic;
    private final SqsPoller assignments;
    private volatile boolean running;

    public SqsDispatchTransport(AssignmentHandler handler,
                                @Value("${app.dispatch.sqs.endpoint:}") String endpoint,
                                @Value("${app.dispatch.sqs.region:ap-south-1}") String region,
                                @Value("${app.dispatch.sqs.booking-requested-url:}") String requestedUrl,
                                @Value("${app.dispatch.sqs.booking-cancelled-url:}") String cancelledUrl,
                                @Value("${app.dispatch.sqs.booking-assignment-url:}") String assignmentUrl) {
        if (requestedUrl.isBlank() || cancelledUrl.isBlank() || assignmentUrl.isBlank()) {
            throw new IllegalStateException("app.dispatch.transport=sqs needs APP_SQS_BOOKING_REQUESTED_URL, "
                    + "APP_SQS_BOOKING_CANCELLED_URL and APP_SQS_BOOKING_ASSIGNMENT_URL");
        }
        SqsClientBuilder b = SqsClient.builder().region(Region.of(region));
        if (!endpoint.isBlank()) {
            // Local SQS emulator (ElasticMQ): any credentials are accepted.
            b.endpointOverride(URI.create(endpoint))
             .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        this.sqs = b.build();
        this.queueByTopic = Map.of(
                Topics.BOOKING_REQUESTED, requestedUrl,
                Topics.BOOKING_CANCELLED, cancelledUrl);
        this.assignments = new SqsPoller("booking-assignment", sqs, assignmentUrl, handler::apply);
        log.info("Dispatch transport = SQS{}", endpoint.isBlank() ? "" : " (local endpoint " + endpoint + ")");
    }

    @Override
    public void send(String topic, String key, String payload) {
        String url = queueByTopic.get(topic);
        if (url == null) throw new IllegalArgumentException("No SQS queue for topic " + topic);
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(url)
                .messageBody(payload)
                .messageAttributes(Map.of("bookingId",
                        MessageAttributeValue.builder().dataType("String").stringValue(key).build()))
                .build());
    }

    @Override public void start() { assignments.start(); running = true; }
    @Override public void stop() { assignments.stop(); sqs.close(); running = false; }
    @Override public boolean isRunning() { return running; }
}
