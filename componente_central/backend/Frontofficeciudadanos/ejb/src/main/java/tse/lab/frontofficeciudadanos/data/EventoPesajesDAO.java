package tse.lab.frontofficeciudadanos.data;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import tse.lab.model.ClaveEvento;
import tse.lab.model.EventoPesajes;

@Stateless
public class EventoPesajesDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public EventoPesajes buscar(ClaveEvento clave) {
        return em.find(EventoPesajes.class, clave);
    }

    public EventoPesajes buscarParaActualizar(ClaveEvento clave) {
        EventoPesajes p=em.find(EventoPesajes.class, clave);
        if (p!=null) em.refresh(p, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return p;
    }

    public void guardar(EventoPesajes pesaje) {
        em.persist(pesaje);
    }

    // Pendientes de deteccion, del mas viejo al mas nuevo
    public List<EventoPesajes> listarPendientes(int limite) {
        return em.createQuery(
                "SELECT p FROM EventoPesajes p WHERE p.detectado = false ORDER BY p.fechaHora, p.matricula",
                EventoPesajes.class)
                .setMaxResults(limite)
                .getResultList();
    }
}
