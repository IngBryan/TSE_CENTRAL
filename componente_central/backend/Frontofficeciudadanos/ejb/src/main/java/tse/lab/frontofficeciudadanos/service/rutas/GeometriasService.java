package tse.lab.frontofficeciudadanos.service.rutas;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.model.RutaReferencia;

/** Transaccion independiente por geometria: permite reconsultar tras conflicto UNIQUE. */
@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
public class GeometriasService {
    @EJB private RutaReferenciaDAO dao;

    public Long obtenerOCrear(String geoJsonCanonico, double distanciaMetros) {
        String hash = RutaReferencia.calcularHuella(geoJsonCanonico);
        RutaReferencia existente = dao.buscarPorHuella(hash);
        if (existente != null) return existente.getId();
        RutaReferencia nueva = dao.guardar(new RutaReferencia(geoJsonCanonico, distanciaMetros));
        dao.sincronizar(); // fuerza constraint UNIQUE antes del retorno
        return nueva.getId();
    }

    public Long buscarId(String hash) {
        RutaReferencia ruta = dao.buscarPorHuella(hash);
        return ruta == null ? null : ruta.getId();
    }
}
