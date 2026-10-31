package tse.lab.trackinggenerator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes the events of one partition of the fleet (vehicle index % partitions) on its own channel,
 * at its share of the profile's rate, and counts the publisher confirms.
 */
final class Publisher implements Runnable {

    static final String EXCHANGE = "tracking";
    static final String ROUTING_KEY = "tracking.event";

    /** Published and not yet confirmed: above this, publishing waits for the broker. */
    private static final int MAX_OUTSTANDING = 5_000;
    private static final Duration TRIP = Duration.ofHours(4);
    private static final ZoneOffset URUGUAY = ZoneOffset.ofHours(-3);
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Body with the field names the central reads. */
    record TrackingMessage(long id, long gpsId, String licensePlate, double latitude, double longitude,
            String datetime) {
    }

    private record Event(String messageId, byte[] body) {
    }

    private static final class Vehicle {
        final int index;
        long clockMicros;
        final long tripStartMicros;
        Event held;

        Vehicle(int index, long clockMicros, long tripStartMicros) {
            this.index = index;
            this.clockMicros = clockMicros;
            this.tripStartMicros = tripStartMicros;
        }
    }

    /**
     * @param rate events per second of the whole fleet at factor 1 (the estimated λ, spread over the
     *             simulated vehicles: each one reports every fleet size / rate seconds)
     */
    record Settings(double rate, Duration duration, LoadProfile profile,
            double duplicateRatio, double outOfOrderRatio) {

        long spacingMicros() {
            return Math.round(1e6 * Fleet.SIZE / rate);
        }
    }

    private final Connection connection;
    private final Settings settings;
    private final int partition;
    private final int partitions;
    private final Stats stats;
    private final AtomicBoolean stop;
    private final long startNanos;
    private final SplittableRandom random;
    private final List<Vehicle> vehicles = new ArrayList<>();
    private final NavigableMap<Long, Boolean> unconfirmed = new ConcurrentSkipListMap<>();

    Publisher(Connection connection, Settings settings, int partition, int partitions, Stats stats,
            AtomicBoolean stop, long startNanos, Instant clocksStart) {
        this.connection = connection;
        this.settings = settings;
        this.partition = partition;
        this.partitions = partitions;
        this.stats = stats;
        this.stop = stop;
        this.startNanos = startNanos;
        this.random = new SplittableRandom(partition);
        long start = clocksStart.toEpochMilli() * 1000;
        for (int i = partition; i < Fleet.SIZE; i += partitions) {
            // Spread the clocks within one spacing and the trips along the route, so vehicles don't move in step
            long clock = start + random.nextLong(settings.spacingMicros());
            vehicles.add(new Vehicle(i, clock, clock - random.nextLong(TRIP.toNanos() / 1000)));
        }
    }

    @Override
    public void run() {
        if (vehicles.isEmpty()) {
            return;
        }
        try (Channel channel = connection.createChannel()) {
            channel.confirmSelect();
            channel.addConfirmListener((tag, multiple) -> settle(tag, multiple, true),
                    (tag, multiple) -> settle(tag, multiple, false));
            // An unroutable message is returned and then confirmed anyway: count it apart, it was lost
            channel.addReturnListener(returned -> stats.unroutable.incrementAndGet());
            publishAtRate(channel);
            for (Vehicle vehicle : vehicles) {
                if (vehicle.held != null) {
                    publish(channel, vehicle.held, false);
                }
            }
            channel.waitForConfirms(30_000);
        } catch (IOException | TimeoutException e) {
            System.err.printf("Publisher %d stopped: %s%n", partition, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void publishAtRate(Channel channel) throws IOException, InterruptedException {
        double baseRate = settings.rate() * vehicles.size() / Fleet.SIZE;
        long endNanos = startNanos + settings.duration().toNanos();
        double due = 0;
        long emitted = 0;
        int cursor = 0;
        long lastNanos = startNanos;
        while (!stop.get()) {
            long now = System.nanoTime();
            if (now >= endNanos) {
                return;
            }
            double elapsed = (now - startNanos) / 1e9;
            due += baseRate * settings.profile().factor(elapsed) * (now - lastNanos) / 1e9;
            lastNanos = now;
            // Above what the broker accepts the publisher falls behind: it catches up for as long as the run lasts
            while (emitted < (long) due && !stop.get() && System.nanoTime() < endNanos) {
                emit(channel, vehicles.get(cursor));
                cursor = (cursor + 1) % vehicles.size();
                emitted++;
            }
            Thread.sleep(5);
        }
    }

    /** One new position of the vehicle; it may be held back one turn (out of order) or sent twice. */
    private void emit(Channel channel, Vehicle vehicle) throws IOException, InterruptedException {
        Event event = nextEvent(vehicle);
        if (vehicle.held == null && random.nextDouble() < settings.outOfOrderRatio()) {
            vehicle.held = event;
            stats.outOfOrder.incrementAndGet();
            return;
        }
        publish(channel, event, false);
        if (vehicle.held != null) {
            publish(channel, vehicle.held, false);
            vehicle.held = null;
        }
        if (random.nextDouble() < settings.duplicateRatio()) {
            publish(channel, event, true);
        }
    }

    private Event nextEvent(Vehicle vehicle) throws IOException {
        Route route = Fleet.route();
        long tripMicros = TRIP.toNanos() / 1000;
        // Goes to the destination and back
        double lap = (double) ((vehicle.clockMicros - vehicle.tripStartMicros) % (2 * tripMicros)) / tripMicros;
        double progress = lap <= 1 ? lap : 2 - lap;
        TrackingMessage message = new TrackingMessage(stats.nextEventId.getAndIncrement(), Fleet.gpsId(vehicle.index),
                Fleet.plate(vehicle.index), route.latitude(progress), route.longitude(progress),
                OffsetDateTime.ofInstant(Instant.ofEpochMilli(vehicle.clockMicros / 1000), URUGUAY).toString());
        vehicle.clockMicros += settings.spacingMicros();
        return new Event(UUID.randomUUID().toString(), JSON.writeValueAsBytes(message));
    }

    private void publish(Channel channel, Event event, boolean duplicate) throws IOException, InterruptedException {
        while (unconfirmed.size() >= MAX_OUTSTANDING && !stop.get()) {
            Thread.sleep(1);
        }
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
                .messageId(event.messageId())
                .contentType("application/json")
                .deliveryMode(2)
                .build();
        unconfirmed.put(channel.getNextPublishSeqNo(), duplicate);
        channel.basicPublish(EXCHANGE, ROUTING_KEY, true, properties, event.body());
        stats.published(duplicate);
    }

    private void settle(long tag, boolean multiple, boolean confirmed) {
        Map<Long, Boolean> settled = multiple ? unconfirmed.headMap(tag, true) : unconfirmed.subMap(tag, true, tag, true);
        for (boolean duplicate : settled.values()) {
            stats.settled(duplicate, confirmed);
        }
        settled.clear();
    }
}
