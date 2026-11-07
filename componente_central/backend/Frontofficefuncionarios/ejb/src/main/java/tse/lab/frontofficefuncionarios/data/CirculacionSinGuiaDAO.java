package tse.lab.frontofficefuncionarios.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import tse.lab.model.CirculacionSinGuia;
import tse.lab.model.EstadoCirculacionSinGuia;
import tse.lab.model.Vehiculo;

@Stateless
public class CirculacionSinGuiaDAO {
    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    /** Serializa todas las transiciones por matrícula, incluso la primera creación. */
    public Vehiculo bloquearVehiculo(String matricula) {
        return em.find(Vehiculo.class, matricula, LockModeType.PESSIMISTIC_WRITE);
    }
    public CirculacionSinGuia actual(String matricula) {
        return em.createQuery("SELECT c FROM CirculacionSinGuia c WHERE c.vehiculo.matricula = :m"
                + " AND c.estado <> :fin ORDER BY c.id DESC", CirculacionSinGuia.class)
                .setParameter("m", matricula)
                .setParameter("fin", EstadoCirculacionSinGuia.FINALIZADA)
                .setMaxResults(1).getResultStream().findFirst().orElse(null);
    }
    public CirculacionSinGuia porCaso(Long idCaso) {
        return em.createQuery("SELECT c FROM CirculacionSinGuia c WHERE c.caso.id = :id", CirculacionSinGuia.class)
                .setParameter("id", idCaso).getResultStream().findFirst().orElse(null);
    }
    /** Solo reanexa mediciones posteriores al inicio y anteriores al fin observado. */
    public CirculacionSinGuia porFecha(String matricula, Instant fecha) {
        return em.createQuery("SELECT c FROM CirculacionSinGuia c WHERE c.vehiculo.matricula = :m"
                + " AND c.caso IS NOT NULL AND c.fechaInicio <= :f"
                + " AND ((c.estado = :activa AND c.ultimaMedicion >= :f)"
                + " OR (c.estado = :fin AND c.fechaFin >= :f))"
                + " ORDER BY c.id DESC", CirculacionSinGuia.class)
                .setParameter("m", matricula).setParameter("f", fecha)
                .setParameter("activa", EstadoCirculacionSinGuia.ACTIVA)
                .setParameter("fin", EstadoCirculacionSinGuia.FINALIZADA)
                .setMaxResults(1).getResultStream().findFirst().orElse(null);
    }
    public List<String> matriculasInactivas(Instant limite, int maximo) {
        return em.createQuery("SELECT c.vehiculo.matricula FROM CirculacionSinGuia c WHERE"
                + " c.estado <> :fin AND c.ultimaRecepcion < :limite ORDER BY c.ultimaRecepcion", String.class)
                .setParameter("fin", EstadoCirculacionSinGuia.FINALIZADA)
                .setParameter("limite", limite).setMaxResults(maximo).getResultList();
    }
    public boolean hayEpisodioPosterior(String matricula, Instant fecha) {
        return !em.createQuery("SELECT c.id FROM CirculacionSinGuia c WHERE c.vehiculo.matricula = :m"
                + " AND c.fechaInicio > :f", Long.class)
                .setParameter("m", matricula).setParameter("f", fecha)
                .setMaxResults(1).getResultList().isEmpty();
    }
    public void guardar(CirculacionSinGuia c) { em.persist(c); }
}
