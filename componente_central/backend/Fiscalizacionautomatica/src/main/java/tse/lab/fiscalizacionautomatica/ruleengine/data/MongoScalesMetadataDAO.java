package tse.lab.fiscalizacionautomatica.ruleengine.data;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;

/** One document per scale in balanzas_metadata: { _id: <scale id>, balanzas_last_timestamp }. */
public class MongoScalesMetadataDAO implements ScalesMetadataDAO {

    public static final String COLLECTION = "balanzas_metadata";
    static final String LAST_TIMESTAMP = "balanzas_last_timestamp";

    private final MongoCollection<Document> collection;

    public MongoScalesMetadataDAO(MongoDatabase database) {
        this.collection = database.getCollection(COLLECTION);
    }

    @Override
    public Optional<Instant> findLastTimestamp(long scaleId) {
        Document document = collection.find(Filters.eq("_id", scaleId)).first();
        return Optional.ofNullable(document)
                .map(d -> d.getDate(LAST_TIMESTAMP))
                .map(Date::toInstant);
    }

    /** Creates the scale document the first time (upsert). */
    @Override
    public void saveLastTimestamp(long scaleId, Instant timestamp) {
        collection.updateOne(Filters.eq("_id", scaleId), Updates.set(LAST_TIMESTAMP, Date.from(timestamp)),
                new UpdateOptions().upsert(true));
    }
}
