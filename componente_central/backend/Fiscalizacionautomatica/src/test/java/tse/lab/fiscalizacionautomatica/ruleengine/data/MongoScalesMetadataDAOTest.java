package tse.lab.fiscalizacionautomatica.ruleengine.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MongoScalesMetadataDAOTest {

    private static final Instant LAST = Instant.parse("2026-10-19T13:00:00Z");

    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final FindIterable<Document> found = mock(FindIterable.class);
    private final MongoScalesMetadataDAO dao;

    MongoScalesMetadataDAOTest() {
        MongoDatabase database = mock(MongoDatabase.class);
        when(database.getCollection(MongoScalesMetadataDAO.COLLECTION)).thenReturn(collection);
        when(collection.find(Filters.eq("_id", 4L))).thenReturn(found);
        dao = new MongoScalesMetadataDAO(database);
    }

    @Test
    void readsTheLastTimestampOfTheScale() {
        when(found.first()).thenReturn(new Document("_id", 4L)
                .append(MongoScalesMetadataDAO.LAST_TIMESTAMP, Date.from(LAST)));

        assertEquals(Optional.of(LAST), dao.findLastTimestamp(4));
    }

    @Test
    void aScaleNeverPolledHasNoTimestamp() {
        assertEquals(Optional.empty(), dao.findLastTimestamp(4));
    }

    @Test
    void savingCreatesTheScaleDocumentTheFirstTime() {
        dao.saveLastTimestamp(4, LAST);

        ArgumentCaptor<UpdateOptions> options = ArgumentCaptor.forClass(UpdateOptions.class);
        verify(collection).updateOne(eq(Filters.eq("_id", 4L)),
                eq(Updates.set(MongoScalesMetadataDAO.LAST_TIMESTAMP, Date.from(LAST))), options.capture());
        assertEquals(true, options.getValue().isUpsert());
    }
}
