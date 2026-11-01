package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import jakarta.annotation.PreDestroy;
import jakarta.ejb.Singleton;
import jakarta.ejb.LocalBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.bson.Document;

/** Lector de la colección tracking que ya administra el consumidor de Fiscalización. */
@Singleton
@LocalBean
public class MongoSeguimientoDAO implements TrackingSeguimientoSource {
    private MongoClient client;

    private static String env(String clave, String defecto) {
        String valor = System.getenv(clave);
        return valor == null || valor.isBlank() ? defecto : valor;
    }

    private synchronized MongoCollection<Document> coleccion() {
        if (client == null) {
            String usuario = System.getenv("MONGO_USER");
            String password = System.getenv("MONGO_PASSWORD");
            if (usuario == null || usuario.isBlank() || password == null || password.isBlank()) {
                throw new SeguimientoNoDisponibleException("MongoDB de seguimiento no configurado");
            }
            try {
                var settings = MongoClientSettings.builder()
                        .applyToClusterSettings(cluster -> cluster
                                .hosts(List.of(new ServerAddress(
                                        env("MONGO_HOST", "localhost"),
                                        Integer.parseInt(env("MONGO_PORT", "27017"))
                                )))
                                .serverSelectionTimeout(3, TimeUnit.SECONDS))
                        .credential(MongoCredential.createCredential(
                                usuario,
                                env("MONGO_AUTH_DB", "admin"),
                                password.toCharArray()))
                        .build();

                client = MongoClients.create(settings);
            } catch (RuntimeException e) {
                throw new SeguimientoNoDisponibleException("No se pudo configurar MongoDB", e);
            }
        }
        return client.getDatabase(env("MONGO_DB", "main_db")).getCollection("tracking");
    }

    @Override
    public List<PuntoGpsSeguimiento> buscar(String matricula, Instant desde, Instant hasta, int limite) {
        return consulta(matricula, desde, hasta, limite, false);
    }

    @Override
    public List<PuntoGpsSeguimiento> recientes(String matricula, Instant desde, Instant hasta, int limite) {
        return consulta(matricula, desde, hasta, limite, true);
    }

    /** Recupera exclusivamente marcas GPS ya vinculadas por PostgreSQL a un expediente.
     * No admite una búsqueda por rango libre que pueda mostrar puntos de otra circulación.
     * MongoDB almacena timestamps en milisegundos; las claves se normalizan a esa precisión.
     */
    public List<PuntoGpsSeguimiento> buscarClaves(String matricula, List<Instant> instantes) {
        if (instantes == null || instantes.isEmpty()) return List.of();
        if (instantes.size() > 500) throw new IllegalArgumentException("Máximo 500 claves por consulta");
        List<Date> fechas = instantes.stream().map(Date::from).distinct().toList();
        try {
            return coleccion().find(Filters.and(Filters.eq("licensePlate", matricula),
                            Filters.in("timestamp", fechas)))
                    .sort(Sorts.ascending("timestamp"))
                    .map(MongoSeguimientoDAO::punto).into(new ArrayList<>());
        } catch (MongoException e) {
            throw new SeguimientoNoDisponibleException("No se pudo consultar las evidencias GPS del caso", e);
        }
    }

    private List<PuntoGpsSeguimiento> consulta(String matricula, Instant desde, Instant hasta, int limite,
            boolean descendente) {
        try {
            return coleccion().find(Filters.and(Filters.eq("licensePlate", matricula),
                            Filters.gte("timestamp", Date.from(desde)), Filters.lt("timestamp", Date.from(hasta))))
                    .sort(descendente ? Sorts.descending("timestamp") : Sorts.ascending("timestamp"))
                    .limit(limite).map(MongoSeguimientoDAO::punto).into(new ArrayList<>());
        } catch (MongoException e) {
            throw new SeguimientoNoDisponibleException("No se pudo consultar el historial GPS", e);
        }
    }

    static PuntoGpsSeguimiento punto(Document d) {
        var coordenadas = d.get("location", Document.class).getList("coordinates", Number.class);
        return new PuntoGpsSeguimiento(d.getString("messageId"), d.getLong("gpsId"),
                d.getString("licensePlate"), coordenadas.get(1).doubleValue(),
                coordenadas.get(0).doubleValue(), d.getDate("timestamp").toInstant());
    }

    @PreDestroy
    public synchronized void cerrar() {
        if (client != null) { client.close(); client = null; }
    }
}
