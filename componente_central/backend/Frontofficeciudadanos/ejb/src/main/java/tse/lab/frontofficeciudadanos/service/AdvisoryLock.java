package tse.lab.frontofficeciudadanos.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;

import javax.sql.DataSource;

// With several WildFly replicas each one has the timers of the jobs: this makes only one of them run a task.
public final class AdvisoryLock {

    private static final String TOMAR = "SELECT pg_try_advisory_lock(?)";
    private static final String LIBERAR = "SELECT pg_advisory_unlock(?)";

    private AdvisoryLock() {
    }

    /**
     * Runs the task if this replica takes the lock; empty if another one holds it. The lock belongs to the session
     * of the connection, not to a transaction, so the task can use its own. If the connection dies before releasing
     * it, PostgreSQL releases it.
     */
    public static <T> Optional<T> correr(DataSource database, long clave, Supplier<T> tarea) throws SQLException {
        try (Connection connection = database.getConnection()) {
            if (!consultar(connection, TOMAR, clave)) {
                return Optional.empty();
            }
            try {
                return Optional.of(tarea.get());
            } finally {
                consultar(connection, LIBERAR, clave);
            }
        }
    }

    private static boolean consultar(Connection connection, String sql, long clave) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, clave);
            try (ResultSet result = query.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
