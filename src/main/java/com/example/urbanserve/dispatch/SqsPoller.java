package com.example.urbanserve.dispatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.function.Consumer;

/**
 * Long-polls one SQS queue on a background thread (same design as urbanserve-dispatch's poller).
 * A message is deleted only after the handler succeeds; if it throws, SQS redelivers it after the
 * visibility timeout, and after maxReceiveCount failures the redrive policy moves it to the DLQ.
 */
public class SqsPoller implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(SqsPoller.class);

    private final String name;
    private final SqsClient sqs;
    private final String queueUrl;
    private final Consumer<String> handler;
    private volatile boolean running;
    private Thread thread;

    public SqsPoller(String name, SqsClient sqs, String queueUrl, Consumer<String> handler) {
        this.name = name;
        this.sqs = sqs;
        this.queueUrl = queueUrl;
        this.handler = handler;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "sqs-" + name);
        thread.setDaemon(true);
        thread.start();
        log.info("Polling SQS queue {} ({})", name, queueUrl);
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
    }

    @Override
    public void run() {
        while (running) {
            try {
                var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(20)   // long polling: cheap, near-instant delivery
                        .maxNumberOfMessages(10)
                        .build()).messages();
                for (Message m : messages) {
                    try {
                        handler.accept(m.body());
                        sqs.deleteMessage(DeleteMessageRequest.builder()
                                .queueUrl(queueUrl).receiptHandle(m.receiptHandle()).build());
                    } catch (Exception e) {
                        log.warn("SQS {}: message {} failed, will be retried: {}", name, m.messageId(), e.toString());
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                log.warn("SQS {}: receive failed ({}); retrying in 5 s", name, e.toString());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
    }
}
