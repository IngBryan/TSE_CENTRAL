package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Message;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Result;

/**
 * Consumes the tracking queue in batches: the messages are saved together in one MongoDB operation and
 * acknowledged one by one by their result. Saving one message at a time bounded each consumer by the
 * round trip to MongoDB.
 */
public class TrackingConsumer extends DefaultConsumer {

    public static final String QUEUE = "tracking.events";

    /** Messages saved together. */
    static final int BATCH_SIZE = 100;
    /** How often a batch that doesn't fill up is saved anyway. */
    static final Duration MAX_WAIT = Duration.ofMillis(50);

    private record Delivery(long tag, Message message) {
    }

    private final TrackingEventReceiver receiver;
    private final int batchSize;
    private final List<Delivery> batch = new ArrayList<>();
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tracking-batch-flush");
        thread.setDaemon(true);
        return thread;
    });

    public TrackingConsumer(Channel channel, TrackingEventReceiver receiver) {
        this(channel, receiver, BATCH_SIZE);
    }

    TrackingConsumer(Channel channel, TrackingEventReceiver receiver, int batchSize) {
        super(channel);
        this.receiver = receiver;
        this.batchSize = batchSize;
    }

    public void start() throws IOException {
        // Twice the batch: the broker keeps delivering while a batch is being saved
        getChannel().basicQos(2 * batchSize);
        getChannel().basicConsume(QUEUE, false, this);
        flusher.scheduleWithFixedDelay(this::flushQuietly, MAX_WAIT.toMillis(), MAX_WAIT.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
            byte[] body) throws IOException {
        String messageId = properties == null ? null : properties.getMessageId();
        batch.add(new Delivery(envelope.getDeliveryTag(), new Message(messageId, body)));
        if (batch.size() >= batchSize) {
            flush();
        }
    }

    /** Saves the pending messages and acknowledges each one by its result. */
    synchronized void flush() throws IOException {
        if (batch.isEmpty()) {
            return;
        }
        List<Delivery> deliveries = List.copyOf(batch);
        batch.clear();
        List<Result> results;
        try {
            results = receiver.receiveAll(deliveries.stream().map(Delivery::message).toList());
        } catch (RuntimeException e) {
            // Saving failed: back to the queue to retry them
            for (Delivery delivery : deliveries) {
                getChannel().basicNack(delivery.tag(), false, true);
            }
            return;
        }
        for (int i = 0; i < deliveries.size(); i++) {
            long tag = deliveries.get(i).tag();
            switch (results.get(i)) {
                case SAVED, DUPLICATE -> getChannel().basicAck(tag, false);
                // Retrying would give the same result: discard it (there is no dead letter queue)
                case INVALID, UNKNOWN_GPS -> getChannel().basicReject(tag, false);
            }
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not save the pending tracking batch: " + e.getMessage());
        }
    }

    @Override
    public synchronized void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {
        // Their delivery tags belong to the closed channel: the broker delivers them again
        batch.clear();
    }
}
