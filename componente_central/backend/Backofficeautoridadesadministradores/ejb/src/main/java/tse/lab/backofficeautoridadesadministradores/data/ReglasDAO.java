package tse.lab.backofficeautoridadesadministradores.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.*;
import java.util.List;
import tse.lab.model.RevisionReglas;
import tse.lab.model.ReglasVigentes;
import tse.lab.backofficeautoridadesadministradores.service.reglas.ReglasException;

@Stateless
public class ReglasDAO {
    @PersistenceContext(unitName="backofficeautoridadesadministradoresPersistenceUnit")
    private EntityManager em;
    public ReglasVigentes vigente(boolean bloquear) {
        ReglasVigentes v=em.find(ReglasVigentes.class, 1L);
        if (v == null) throw new ReglasException(503, "Falta inicializar la configuración de reglas");
        if (bloquear) em.refresh(v, LockModeType.PESSIMISTIC_WRITE);
        return v;
    }
    public RevisionReglas revision(long numero) {
        RevisionReglas r=em.find(RevisionReglas.class, numero);
        if (r == null) throw new ReglasException(404, "Revisión de reglas inexistente");
        return r;
    }
    public void guardar(RevisionReglas r) { em.persist(r); em.flush(); }
    public List<RevisionReglas> historial() {
        return em.createQuery("SELECT r FROM RevisionReglas r ORDER BY r.revision DESC", RevisionReglas.class)
                .setMaxResults(20).getResultList();
    }
    /** Serializa publicadores de varios WildFly y relee el estado después del bloqueo. */
    public RevisionReglas siguientePendiente() {
        var ids=em.createQuery("SELECT r.revision FROM RevisionReglas r WHERE r.publicadaEn IS NULL ORDER BY r.revision", Long.class)
                .setMaxResults(1).getResultList();
        if (ids.isEmpty()) return null;
        RevisionReglas r=em.find(RevisionReglas.class, ids.getFirst());
        em.refresh(r, LockModeType.PESSIMISTIC_WRITE);
        return r.pendiente() ? r : null;
    }
}
