package tse.lab.fiscalizacionautomatica.ruleengine.routes;

import java.io.IOException;
import java.util.List;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

/** Consulta de referencia para rutas ya autorizadas, sin recalcularlas con OSRM. */
@FunctionalInterface
public interface RutasAutorizadasSource {
    List<DatosRutaAutorizada> obtenerPorGuia(String idGuia) throws IOException;
}
