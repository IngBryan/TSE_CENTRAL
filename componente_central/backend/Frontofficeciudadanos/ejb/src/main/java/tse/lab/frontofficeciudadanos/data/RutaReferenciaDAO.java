package tse.lab.frontofficeciudadanos.data;

import java.util.List;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tse.lab.model.GuiaRutaAutorizada;
import tse.lab.model.RutaReferencia;

/** Consulta y registra geometrías compartidas; la selección vial es responsabilidad de la tarea 2. */
@Stateless
public class RutaReferenciaDAO {

    @PersistenceContext(unitName = "frontofficeciudadanosPersistenceUnit")
    private EntityManager em;

    public RutaReferencia buscarPorHuella(String sha256GeoJson) {
        return em.createQuery(
                "SELECT r FROM RutaReferencia r WHERE r.sha256GeoJson = :sha", RutaReferencia.class)
                .setParameter("sha", sha256GeoJson)
                .getResultStream().findFirst().orElse(null);
    }

    public RutaReferencia buscarPorId(Long id) {
        return id == null ? null : em.find(RutaReferencia.class, id);
    }

    public void sincronizar() {
        em.flush();
    }

    public RutaReferencia guardar(RutaReferencia ruta) {
        em.persist(ruta);
        return ruta;
    }

    public List<GuiaRutaAutorizada> buscarPorGuia(String guiaId) {
        return em.createQuery(
                "SELECT gr FROM GuiaRutaAutorizada gr JOIN FETCH gr.rutaReferencia "
                    + "WHERE gr.guia.id = :guiaId ORDER BY gr.numeroAlternativa",
                GuiaRutaAutorizada.class)
                .setParameter("guiaId", guiaId)
                .getResultList();
    }
}
