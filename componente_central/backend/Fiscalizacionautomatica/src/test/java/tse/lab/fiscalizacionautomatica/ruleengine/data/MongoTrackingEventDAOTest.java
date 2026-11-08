package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.bulk.BulkWriteError;
import com.mongodb.bulk.BulkWriteResult;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.InsertManyOptions;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.bson.BsonDocument;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;

class MongoTrackingEventDAOTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-10-10T15:30:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-10-10T15:30:02Z");
    private static final TrackingEvent EVENT = new TrackingEvent("msg-1", 7, 1, "SBC1234", -34.9011, -56.1645, TIMESTAMP);
    private static final TrackingEvent LATER = new TrackingEvent("msg-2", 8, 1, "SBC1234", -34.9011, -56.1645,
            TIMESTAMP.plusSeconds(5));
    private static final int DUPLICATE_KEY = 11000;

    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> collection = mock(MongoCollection.class);

    private MongoTrackingEventDAO dao() {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(MongoTrackingEventDAO.COLLECTION)).thenReturn(collection);
        return new MongoTrackingEventDAO(database, Clock.fixed(RECEIVED_AT, ZoneOffset.UTC));
    }

    private static MongoBulkWriteException bulkWriteError(int code, int index) {
        return new MongoBulkWriteException(BulkWriteResult.unacknowledged(),
                List.of(new BulkWriteError(code, "error", new BsonDocument(), index)), null, new ServerAddress(),
                Set.of());
    }

    @Test
    void documentHasTheEventDataAndIsPendingDetection() {
        Document expected = new Document("messageId", "msg-1")
                .append("externalId", 7L)
                .append("gpsId", 1L)
                .append("licensePlate", "SBC1234")
                .append("location", new Document("type", "Point").append("coordinates", List.of(-56.1645, -34.9011)))
                .append("timestamp", Date.from(TIMESTAMP))
                .append("receivedAt", Date.from(RECEIVED_AT))
                .append("detected", false);
        assertEquals(expected, MongoTrackingEventDAO.toDocument(EVENT, RECEIVED_AT));
    }

    @Test
    void readsBackTheSavedEvent() {
        assertEquals(EVENT, MongoTrackingEventDAO.fromDocument(MongoTrackingEventDAO.toDocument(EVENT, RECEIVED_AT)));
    }

    @Test
    void savesTheBatchInOneUnorderedInsert() {
        assertEquals(List.of(true, true), dao().saveAll(List.of(EVENT, LATER)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documents = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<InsertManyOptions> options = ArgumentCaptor.forClass(InsertManyOptions.class);
        verify(collection).insertMany(documents.capture(), options.capture());
        assertEquals(List.of(MongoTrackingEventDAO.toDocument(EVENT, RECEIVED_AT),
                MongoTrackingEventDAO.toDocument(LATER, RECEIVED_AT)), documents.getValue());
        assertEquals(false, options.getValue().isOrdered());
    }

    @Test
    void eventsRejectedByTheUniqueIndexAreReportedAsNotSaved() {
        when(collection.insertMany(anyList(), any(InsertManyOptions.class))).thenThrow(bulkWriteError(DUPLICATE_KEY, 0));

        assertEquals(List.of(false, true), dao().saveAll(List.of(EVENT, LATER)));
    }

    @Test
    void anyOtherWriteErrorFailsTheBatch() {
        when(collection.insertMany(anyList(), any(InsertManyOptions.class))).thenThrow(bulkWriteError(2, 1));

        assertThrows(MongoBulkWriteException.class, () -> dao().saveAll(List.of(EVENT, LATER)));
    }

    @Test
    void anEmptyBatchDoesNotGoToMongo() {
        assertEquals(List.of(), dao().saveAll(List.of()));

        verify(collection, never()).insertMany(anyList(), any(InsertManyOptions.class));
    }
    @Test
    void fijarRevisionConservaLaGanadoraYNoSobrescribeAlReprocesar() {
        @SuppressWarnings("unchecked")
        com.mongodb.client.FindIterable<Document> consulta=mock(com.mongodb.client.FindIterable.class);
        when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(consulta);
        when(consulta.projection(any(org.bson.conversions.Bson.class))).thenReturn(consulta);
        when(consulta.first()).thenReturn(new Document("revisionReglas",2L));
        var dao=dao();
        assertEquals(2,dao.fijarRevisionReglas(EVENT,5));
        var filtro=ArgumentCaptor.forClass(org.bson.conversions.Bson.class);
        verify(collection).updateOne(filtro.capture(),any(org.bson.conversions.Bson.class));
        var json=filtro.getValue().toBsonDocument(Document.class,com.mongodb.MongoClientSettings.getDefaultCodecRegistry()).toJson();
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("revisionReglas"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("$exists"));
        org.junit.jupiter.api.Assertions.assertTrue(json.contains("false"));
    }
    @Test
    void unGpsSinRevisionPersistidaNoSeMarcaComoEvaluado() {
        @SuppressWarnings("unchecked")
        com.mongodb.client.FindIterable<Document> consulta=mock(com.mongodb.client.FindIterable.class);
        when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(consulta);
        when(consulta.projection(any(org.bson.conversions.Bson.class))).thenReturn(consulta);
        var dao=dao();
        assertThrows(IllegalStateException.class,()->dao.fijarRevisionReglas(EVENT,2));
        when(consulta.first()).thenReturn(new Document("revisionReglas","invalida"));
        assertThrows(IllegalStateException.class,()->dao.fijarRevisionReglas(EVENT,2));
    }

}
