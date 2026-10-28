package tse.lab.frontofficefuncionarios.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.Guia;

// Solo lectura: los eventos los registra el chofer desde la app movil (Frontofficeciudadanos)
@Stateless
public class EventoViajeDAO {

    @PersistenceContext(unitName = "frontofficefuncionariosPersistenceUnit")
    private EntityManager em;

    // Los eventos de la guia sin la foto, ordenados por la hora del dispositivo y despues por la de recepcion
    // (mismo orden que ve el responsable)
    public List<EventoViajeFila> buscarPorGuia(Guia guia) {
        return em.createQuery("SELECT new tse.lab.frontofficefuncionarios.data.EventoViajeFila(e.idEvento, e.tipo,"
                + " e.ciChofer, e.fechaHoraDispositivo, e.fechaRecepcion, e.descripcion, e.latitud, e.longitud,"
                + " CASE WHEN e.foto IS NULL THEN FALSE ELSE TRUE END, e.resultado, e.codigo, e.detalle)"
                + " FROM EventoViaje e WHERE e.guia = :guia ORDER BY e.fechaHoraDispositivo, e.fechaRecepcion",
                EventoViajeFila.class)
                .setParameter("guia", guia)
                .getResultList();
    }

    // La foto del evento de esa guia. Lista vacia si el evento no existe o es de otra guia; un elemento
    // null si existe pero no tiene foto.
    public List<byte[]> foto(String idGuia, String idEvento) {
        return em.createQuery("SELECT e.foto FROM EventoViaje e WHERE e.idEvento = :idEvento AND e.guia.id = :idGuia",
                byte[].class)
                .setParameter("idEvento", idEvento)
                .setParameter("idGuia", idGuia)
                .getResultList();
    }
}
