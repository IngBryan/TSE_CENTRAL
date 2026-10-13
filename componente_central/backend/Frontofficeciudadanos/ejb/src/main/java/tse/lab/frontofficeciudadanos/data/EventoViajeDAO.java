package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.EventoViaje;
import tse.lab.model.Guia;

@Stateless
public class EventoViajeDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public EventoViaje buscarPorId(String idEvento) {
        return em.find(EventoViaje.class, idEvento);
    }

    // persist + flush: si otro request ya inserto el mismo idEvento, la clave primaria falla aca
    public void guardar(EventoViaje evento) {
        em.persist(evento);
        em.flush();
    }

    public List<EventoViaje> buscarPorGuia(Guia guia) {
        return em.createQuery(
                "SELECT e FROM EventoViaje e WHERE e.guia = :guia ORDER BY e.fechaHoraDispositivo, e.fechaRecepcion",
                EventoViaje.class)
                .setParameter("guia", guia)
                .getResultList();
    }
}
