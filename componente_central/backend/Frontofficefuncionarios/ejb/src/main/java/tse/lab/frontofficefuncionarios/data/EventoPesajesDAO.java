package tse.lab.frontofficefuncionarios.data;

import java.time.Instant;
import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import tse.lab.model.EventoPesajes;

// Solo lectura: los pesajes los guarda el poller de fiscalizacion automatica
@Stateless
public class EventoPesajesDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Pesajes del vehiculo entre desde y hasta (inclusive), del mas viejo al mas nuevo, con su balanza.
    // hasta null: sin limite (viaje en curso).
    public List<EventoPesajes> buscarPorMatriculaEntre(String matricula, Instant desde, Instant hasta) {
        TypedQuery<EventoPesajes> q = em.createQuery("SELECT p FROM EventoPesajes p JOIN FETCH p.nodoBalanza"
                + " WHERE p.matricula = :matricula AND p.fechaHora >= :desde"
                + (hasta == null ? "" : " AND p.fechaHora <= :hasta") + " ORDER BY p.fechaHora", EventoPesajes.class)
                .setParameter("matricula", matricula)
                .setParameter("desde", desde);
        if (hasta != null) {
            q.setParameter("hasta", hasta);
        }
        return q.getResultList();
    }
}
