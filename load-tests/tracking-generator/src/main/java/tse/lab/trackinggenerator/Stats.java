package tse.lab.trackinggenerator;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters shared by the publishers. Unique events confirmed by RabbitMQ is the number to compare
 * with what the central saved: duplicates must not add to it.
 */
final class Stats {

    final AtomicLong uniquePublished = new AtomicLong();
    final AtomicLong duplicatesPublished = new AtomicLong();
    final AtomicLong outOfOrder = new AtomicLong();
    final AtomicLong uniqueConfirmed = new AtomicLong();
    final AtomicLong duplicatesConfirmed = new AtomicLong();
    final AtomicLong rejected = new AtomicLong();
    final AtomicLong unroutable = new AtomicLong();
    final AtomicLong outstanding = new AtomicLong();
    final AtomicLong nextEventId = new AtomicLong(1);

    void published(boolean duplicate) {
        (duplicate ? duplicatesPublished : uniquePublished).incrementAndGet();
        outstanding.incrementAndGet();
    }

    void settled(boolean duplicate, boolean confirmed) {
        outstanding.decrementAndGet();
        if (!confirmed) {
            rejected.incrementAndGet();
        } else {
            (duplicate ? duplicatesConfirmed : uniqueConfirmed).incrementAndGet();
        }
    }
}
