package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.EventoViaje;
import tse.lab.model.Guia;
import tse.lab.model.ResultadoEventoViaje;
import tse.lab.model.TipoEventoViaje;

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

    /**
     * Proyección mínima de los límites temporales reales: [idGuia, tipo, fechaDispositivo].
     * No carga fotografías ni incidentes en memoria y excluye eventos rechazados
     * (CONFLICTO) y repetidos (SIN_EFECTO).
     */
    public List<Object[]> buscarLimitesAplicados(List<Guia> guias) {
        if (guias.isEmpty()) {
            return List.of();
        }
        return em.createQuery(
                "SELECT e.guia.id, e.tipo, e.fechaHoraDispositivo FROM EventoViaje e "
                        + "WHERE e.guia IN :guias AND e.resultado = :aplicado AND e.tipo IN :tipos "
                        + "ORDER BY e.fechaHoraDispositivo, e.idEvento",
                Object[].class)
                .setParameter("guias", guias)
                .setParameter("aplicado", ResultadoEventoViaje.APLICADO)
                .setParameter("tipos", List.of(TipoEventoViaje.INICIO, TipoEventoViaje.FIN))
                .getResultList();
    }

    public List<EventoViaje> buscarPorGuia(Guia guia) {
        return em.createQuery(
                "SELECT e FROM EventoViaje e WHERE e.guia = :guia ORDER BY e.fechaHoraDispositivo, e.fechaRecepcion",
                EventoViaje.class)
                .setParameter("guia", guia)
                .getResultList();
    }
}
