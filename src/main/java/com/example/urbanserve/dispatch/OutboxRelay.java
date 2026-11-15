package com.example.urbanserve.dispatch;

import com.example.urbanserve.model.OutboxEvent;
import com.example.urbanserve.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * Sends unsent outbox messages in order. A message is marked sent only after SQS accepts it; if the
 * app stops between the two, it is sent again on restart (dispatch handles duplicates). On a send
 * failure the batch stops and is retried on the next tick, keeping messages in order.
 * Runs on a single instance; with several instances, add row locking (SELECT ... FOR UPDATE SKIP LOCKED).
 */
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final DispatchTransport transport;
    private final TransactionTemplate tx;

    public OutboxRelay(OutboxRepository outbox, DispatchTransport transport, TransactionTemplate tx) {
        this.outbox = outbox;
        this.transport = transport;
        this.tx = tx;
    }

    @Scheduled(fixedDelayString = "${app.dispatch.relay-interval:PT2S}")
    public void relay() {
        for (OutboxEvent e : outbox.findTop50BySentAtIsNullOrderByIdAsc()) {
            try {
                transport.send(e.getTopic(), e.getMessageKey(), e.getPayload());
                tx.executeWithoutResult(s -> outbox.findById(e.getId()).ifPresent(x -> { x.markSent(); outbox.save(x); }));
            } catch (Exception ex) {
                tx.executeWithoutResult(s -> outbox.findById(e.getId()).ifPresent(x -> { x.markFailed(ex.toString()); outbox.save(x); }));
                if (e.getAttempts() == 0 || e.getAttempts() % 30 == 0) {
                    log.warn("Could not send {} for booking #{} (attempt {}): {}. Will retry.",
                            e.getTopic(), e.getMessageKey(), e.getAttempts() + 1, ex.toString());
                }
                return; // keep order: retry this one first on the next tick
            }
        }
    }

    /** Housekeeping: drop sent messages older than 7 days. */
    @Scheduled(fixedDelayString = "PT6H")
    @Transactional
    public void purgeSent() {
        outbox.deleteSentBefore(LocalDateTime.now().minusDays(7));
    }
}
