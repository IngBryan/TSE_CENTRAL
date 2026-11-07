package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.Sorts;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MongoSeguimientoDAOTest {
    private static final Instant T = Instant.parse("2026-11-01T12:00:00Z");
    private static final PuntoGpsSeguimiento PUNTO = new PuntoGpsSeguimiento("m", 1, "SBC1234", -34.5, -56.2, T);

    private final MongoClient client = mock(MongoClient.class);
    private final MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    private final MongoCollection<Document> coleccion = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    private final FindIterable<Document> cursor = mock(FindIterable.class);
    @SuppressWarnings("unchecked")
    private final MongoIterable<PuntoGpsSeguimiento> mapeado = mock(MongoIterable.class);
    private final MongoSeguimientoDAO dao = new MongoSeguimientoDAO();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        conectado(dao, client);
        when(client.getDatabase(anyString())).thenReturn(database);
        when(database.getCollection("tracking")).thenReturn(coleccion);
        when(coleccion.find(any(Bson.class))).thenReturn(cursor);
        when(cursor.sort(any())).thenReturn(cursor);
        when(cursor.limit(anyInt())).thenReturn(cursor);
        doReturn(mapeado).when(cursor).map(any());
        doAnswer(invocacion -> {
            Collection<PuntoGpsSeguimiento> destino = invocacion.getArgument(0);
            destino.add(PUNTO);
            return destino;
        }).when(mapeado).into(any());
    }

    private static void conectado(MongoSeguimientoDAO dao, MongoClient client) throws Exception {
        Field f = MongoSeguimientoDAO.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(dao, client);
    }

    @Test
    void theHistoryGoesFromTheOldestPosition() {
        assertEquals(List.of(PUNTO), dao.buscar("SBC1234", T, T.plusSeconds(60), 10));

        verify(cursor).sort(Sorts.ascending("timestamp"));
        verify(cursor).limit(10);
    }

    @Test
    void theRecentOnesGoFromTheNewestPosition() {
        assertEquals(List.of(PUNTO), dao.recientes("SBC1234", T, T.plusSeconds(60), 5));

        verify(cursor).sort(Sorts.descending("timestamp"));
        verify(cursor).limit(5);
    }

    @Test
    void readsTheEvidenceByItsKeys() {
        assertEquals(List.of(PUNTO), dao.buscarClaves("SBC1234", List.of(T, T)));

        verify(cursor).sort(Sorts.ascending("timestamp"));
    }

    @Test
    void withoutKeysItDoesNotQuery() {
        assertTrue(dao.buscarClaves("SBC1234", null).isEmpty());
        assertTrue(dao.buscarClaves("SBC1234", List.of()).isEmpty());

        verify(coleccion, never()).find(any(Bson.class));
    }

    @Test
    void thereIsALimitOfKeysPerQuery() {
        List<Instant> claves = Collections.nCopies(501, T);

        assertThrows(IllegalArgumentException.class, () -> dao.buscarClaves("SBC1234", claves));
    }

    @Test
    void aMongoFailureIsReportedAsUnavailable() {
        when(coleccion.find(any(Bson.class))).thenThrow(new MongoException("down"));
        List<Instant> claves = List.of(T);

        assertThrows(SeguimientoNoDisponibleException.class, () -> dao.buscar("SBC1234", T, T.plusSeconds(1), 1));
        assertThrows(SeguimientoNoDisponibleException.class, () -> dao.buscarClaves("SBC1234", claves));
    }

    @Test
    void withoutCredentialsItIsUnavailable() {
        assumeTrue(System.getenv("MONGO_USER") == null);
        MongoSeguimientoDAO sinConfigurar = new MongoSeguimientoDAO();

        assertThrows(SeguimientoNoDisponibleException.class,
                () -> sinConfigurar.buscar("SBC1234", T, T.plusSeconds(1), 1));
    }

    @Test
    void closesTheClientOnlyOnce() {
        dao.cerrar();
        dao.cerrar();

        verify(client).close();
    }

    @Test
    void buildsAClientWithTheCredentials() {
        try (MongoClient nuevo = MongoSeguimientoDAO.crearCliente("usuario", "clave")) {
            assertNotNull(nuevo);
        }
    }

    @Test
    void convertsADocumentWithLatitudeSecond() {
        Document documento = new Document("messageId", "m").append("gpsId", 1L).append("licensePlate", "SBC1234")
                .append("timestamp", Date.from(T))
                .append("location", new Document("coordinates", List.of(-56.2, -34.5)));

        assertEquals(PUNTO, MongoSeguimientoDAO.punto(documento));
    }
}
