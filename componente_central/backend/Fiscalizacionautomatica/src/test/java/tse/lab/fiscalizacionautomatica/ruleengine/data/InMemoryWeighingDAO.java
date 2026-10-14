package tse.lab.fiscalizacionautomatica.ruleengine.data;

import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tse.lab.fiscalizacionautomatica.ruleengine.model.Weighing;

/** In-memory storage for tests, ignoring duplicates the same way eventos_pesajes does (by license plate and timestamp). */
public class InMemoryWeighingDAO implements WeighingDAO {

    private record Key(String licensePlate, Instant timestamp) {
        static Key of(Weighing weighing) {
            return new Key(weighing.licensePlate(), weighing.timestamp());
        }
    }

    private record Stored(Weighing weighing, boolean detected) {
    }

    private final Map<Key, Stored> weighings = new LinkedHashMap<>();
    /** While true, every call fails as if WildFly were down. */
    public boolean down;

    @Override
    public int save(List<Weighing> toSave) throws IOException {
        failIfDown();
        int saved = 0;
        for (Weighing weighing : toSave) {
            if (weighings.putIfAbsent(Key.of(weighing), new Stored(weighing, false)) == null) {
                saved++;
            }
        }
        return saved;
    }

    @Override
    public List<Weighing> findPending(int limit) throws IOException {
        failIfDown();
        return weighings.values().stream()
                .filter(stored -> !stored.detected())
                .map(Stored::weighing)
                .sorted(Comparator.comparing(Weighing::timestamp))
                .limit(limit)
                .toList();
    }

    @Override
    public void markDetected(Weighing weighing) throws IOException {
        failIfDown();
        weighings.computeIfPresent(Key.of(weighing), (key, stored) -> new Stored(stored.weighing(), true));
    }

    public List<Weighing> saved() {
        return weighings.values().stream().map(Stored::weighing).toList();
    }

    public boolean isDetected(Weighing weighing) {
        Stored stored = weighings.get(Key.of(weighing));
        return stored != null && stored.detected();
    }

    private void failIfDown() throws IOException {
        if (down) {
            throw new IOException("WildFly down");
        }
    }
}
