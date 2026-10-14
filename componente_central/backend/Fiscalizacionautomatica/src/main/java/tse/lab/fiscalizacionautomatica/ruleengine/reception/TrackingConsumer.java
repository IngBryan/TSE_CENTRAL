package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Result;

public class TrackingConsumer extends DefaultConsumer {

    public static final String QUEUE = "tracking.events";

    /** Maximum number of delivered, unacknowledged messages. */
    private static final int PREFETCH = 20;

    private final TrackingEventReceiver receiver;

    public TrackingConsumer(Channel channel, TrackingEventReceiver receiver) {
        super(channel);
        this.receiver = receiver;
    }

    public void start() throws IOException {
        getChannel().basicQos(PREFETCH);
        getChannel().basicConsume(QUEUE, false, this);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
            byte[] body) throws IOException {
        long tag = envelope.getDeliveryTag();
        String messageId = properties == null ? null : properties.getMessageId();
        Result result;
        try {
            result = receiver.receive(messageId, body);
        } catch (RuntimeException e) {
            // Saving failed: back to the queue to retry it
            getChannel().basicNack(tag, false, true);
            return;
        }
        switch (result) {
            case SAVED, DUPLICATE -> getChannel().basicAck(tag, false);
            // Retrying would give the same result: discard it (there is no dead letter queue)
            case INVALID, UNKNOWN_GPS -> getChannel().basicReject(tag, false);
        }
    }
}
