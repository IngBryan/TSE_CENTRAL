package tse.lab.fiscalizacionautomatica.ruleengine.reception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Message;
import tse.lab.fiscalizacionautomatica.ruleengine.reception.TrackingEventReceiver.Result;

class TrackingConsumerTest {

    private static final byte[] BODY = "{}".getBytes();
    private static final AMQP.BasicProperties PROPERTIES = new AMQP.BasicProperties.Builder().messageId("msg-1")
            .build();

    private final Channel channel = mock(Channel.class);
    private final TrackingEventReceiver receiver = mock(TrackingEventReceiver.class);

    private static void deliver(TrackingConsumer consumer, long tag, AMQP.BasicProperties properties)
            throws IOException {
        consumer.handleDelivery("consumer", new Envelope(tag, false, "", TrackingConsumer.QUEUE), properties, BODY);
    }

    @Test
    void startsConsumingTheQueueWithManualAckAndAPrefetchOfTwoBatches() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 10);

        consumer.start();

        verify(channel).basicQos(20);
        verify(channel).basicConsume(TrackingConsumer.QUEUE, false, consumer);
    }

    @Test
    void savesTheMessagesTogetherOnceTheBatchIsFull() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 3);
        when(receiver.receiveAll(any())).thenReturn(List.of(Result.SAVED, Result.SAVED, Result.SAVED));

        deliver(consumer, 1, PROPERTIES);
        deliver(consumer, 2, PROPERTIES);
        verify(receiver, never()).receiveAll(any());
        deliver(consumer, 3, PROPERTIES);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Message>> batch = ArgumentCaptor.forClass(List.class);
        verify(receiver).receiveAll(batch.capture());
        assertEquals(3, batch.getValue().size());
        // A single ack for the whole batch
        verify(channel).basicAck(3, true);
        verify(channel, times(1)).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void flushSavesABatchThatDidNotFillUp() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 100);
        when(receiver.receiveAll(any())).thenReturn(List.of(Result.SAVED));

        deliver(consumer, 1, PROPERTIES);
        consumer.flush();

        verify(channel).basicAck(1, true);
    }

    @Test
    void flushWithoutPendingMessagesDoesNothing() throws IOException {
        new TrackingConsumer(channel, receiver, 100).flush();

        verify(receiver, never()).receiveAll(any());
    }

    @Test
    void discardsTheMessagesThatCantBeSavedBeforeAcknowledgingTheRestUpToTheLastValidOne() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 4);
        when(receiver.receiveAll(any()))
                .thenReturn(List.of(Result.INVALID, Result.SAVED, Result.DUPLICATE, Result.UNKNOWN_GPS));

        deliver(consumer, 1, null);
        deliver(consumer, 2, PROPERTIES);
        deliver(consumer, 3, PROPERTIES);
        deliver(consumer, 4, PROPERTIES);

        // Retrying would give the same result: discarded without requeue, before the ack that would cover them
        InOrder order = inOrder(channel);
        order.verify(channel).basicReject(1, false);
        order.verify(channel).basicReject(4, false);
        // Up to the last valid one: acking a rejected tag closes the channel
        order.verify(channel).basicAck(3, true);
        verify(channel, times(1)).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void aBatchWithoutValidMessagesIsNotAcknowledged() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 2);
        when(receiver.receiveAll(any())).thenReturn(List.of(Result.INVALID, Result.UNKNOWN_GPS));

        deliver(consumer, 1, null);
        deliver(consumer, 2, PROPERTIES);

        verify(channel).basicReject(1, false);
        verify(channel).basicReject(2, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void ifSavingFailsTheWholeBatchGoesBackToTheQueue() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 2);
        when(receiver.receiveAll(any())).thenThrow(new IllegalStateException("MongoDB down"));

        deliver(consumer, 1, PROPERTIES);
        deliver(consumer, 2, PROPERTIES);

        verify(channel).basicNack(2, true, true);
        verify(channel, times(1)).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void aClosedChannelDiscardsThePendingMessagesForTheBrokerToDeliverThemAgain() throws IOException {
        TrackingConsumer consumer = new TrackingConsumer(channel, receiver, 100);
        deliver(consumer, 1, PROPERTIES);

        consumer.handleShutdownSignal("consumer", new ShutdownSignalException(false, false, null, null));
        consumer.flush();

        verify(receiver, never()).receiveAll(any());
    }
}
