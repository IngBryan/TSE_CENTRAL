package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Result;

class TrackingConsumerTest {

    private static final byte[] BODY = "{}".getBytes();
    private static final AMQP.BasicProperties PROPERTIES = new AMQP.BasicProperties.Builder().messageId("msg-1")
            .build();

    private final Channel channel = mock(Channel.class);
    private final TrackingEventReceiver receiver = mock(TrackingEventReceiver.class);
    private final TrackingConsumer consumer = new TrackingConsumer(channel, receiver);

    private void deliver(long tag, AMQP.BasicProperties properties) throws IOException {
        consumer.handleDelivery("consumer", new Envelope(tag, false, "", TrackingConsumer.QUEUE), properties, BODY);
    }

    @Test
    void startsConsumingTheQueueWithManualAckAndAPrefetch() throws IOException {
        consumer.start();

        verify(channel).basicQos(anyInt());
        verify(channel).basicConsume(TrackingConsumer.QUEUE, false, consumer);
    }

    @Test
    void savedAndDuplicateEventsAreAcknowledged() throws IOException {
        when(receiver.receive("msg-1", BODY)).thenReturn(Result.SAVED, Result.DUPLICATE);

        deliver(1, PROPERTIES);
        deliver(2, PROPERTIES);

        verify(channel).basicAck(1, false);
        verify(channel).basicAck(2, false);
    }

    @Test
    void invalidEventsAndUnknownGpsAreDiscardedWithoutRequeue() throws IOException {
        when(receiver.receive(any(), eq(BODY))).thenReturn(Result.INVALID, Result.UNKNOWN_GPS);

        deliver(1, null);
        deliver(2, PROPERTIES);

        verify(channel).basicReject(1, false);
        verify(channel).basicReject(2, false);
    }

    @Test
    void ifSavingFailsTheMessageGoesBackToTheQueue() throws IOException {
        when(receiver.receive("msg-1", BODY)).thenThrow(new IllegalStateException("MongoDB down"));

        deliver(1, PROPERTIES);

        verify(channel).basicNack(1, false, true);
    }
}
