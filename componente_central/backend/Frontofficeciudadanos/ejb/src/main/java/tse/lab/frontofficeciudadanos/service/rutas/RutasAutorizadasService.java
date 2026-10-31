package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.List;
import jakarta.ejb.EJB;
import jakarta.ejb.LocalBean;
import jakarta.ejb.Stateless;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;

/** Consulta centralizada: geometrías persistidas, inmutables y compartidas entre Guías. */
@Stateless
@LocalBean
public class RutasAutorizadasService implements RutasAutorizadasRemote {
    @EJB private RutaReferenciaDAO rutas;

    @Override
    public List<DatosRutaAutorizada> obtenerPorGuia(String idGuia) {
        if (idGuia == null || idGuia.isBlank()) return List.of();
        return rutas.buscarPorGuia(idGuia).stream().map(gr -> {
            var ruta = gr.getRutaReferencia();
            return new DatosRutaAutorizada(ruta.getId(), gr.getNumeroAlternativa(),
                    ruta.getSha256GeoJson(), ruta.getDistanciaMetros(), ruta.getGeometriaGeoJson());
        }).toList();
    }
}
