package tse.lab.fiscalizacionautomatica.ruleengine.data;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

/**
 * Saves the events in the MongoDB tracking collection. The _id is the message_id: a redelivered
 * message hits the unique _id index and is not saved twice.
 */
public class MongoTrackingEventDAO implements TrackingEventDAO {

    public static final String COLLECTION = "tracking";

    private final MongoCollection<Document> collection;

    public MongoTrackingEventDAO(MongoDatabase database) {
        this.collection = database.getCollection(COLLECTION);
    }

    @Override
    public boolean save(TrackingEvent event) {
        try {
            collection.insertOne(toDocument(event));
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw e;
        }
    }

    /** The location is stored as a GeoJSON point ([longitude, latitude]) to allow geospatial queries. */
    static Document toDocument(TrackingEvent event) {
        return new Document("_id", event.messageId())
                .append("eventId", event.eventId())
                .append("gpsId", event.gpsId())
                .append("licensePlate", event.licensePlate())
                .append("location", new Document("type", "Point")
                        .append("coordinates", List.of(event.longitude(), event.latitude())))
                .append("timestamp", Date.from(event.timestamp()));
    }
}
