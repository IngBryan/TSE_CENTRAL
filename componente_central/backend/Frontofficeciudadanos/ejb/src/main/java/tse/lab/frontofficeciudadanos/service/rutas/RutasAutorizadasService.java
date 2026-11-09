package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;
import tse.lab.model.RutaReferencia;
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
        // Las guias anteriores ya estaban ordenadas por duracion: conservar solo la principal.
        // Las evidencias historicas siguen consultando todas sus geometrías por huella.
        return rutas.buscarPorGuia(idGuia).stream().limit(1).map(gr -> {
            var ruta = gr.getRutaReferencia();
            return new DatosRutaAutorizada(ruta.getId(), gr.getNumeroAlternativa(),
                    ruta.getSha256GeoJson(), ruta.getDistanciaMetros(), ruta.getGeometriaGeoJson());
        }).toList();
    }
    /** Consulta los GeoJSON inmutables que corresponden a huellas de evidencias persistidas.
     * Las huellas se obtienen en Funcionarios desde el expediente, nunca del navegador.
     * Una fila ausente o adulterada NO se presenta como ruta histórica verificada.
     */
    @Override
    public List<DatosRutaAutorizada> obtenerPorHuellas(List<String> huellas) {
        if (huellas == null || huellas.isEmpty()) return List.of();
        // Acotar el coste por solicitud sin limitar la cantidad de confirmaciones del caso.
        Set<String> unicas = new HashSet<>();
        List<String> buscadas = huellas.stream()
                .filter(h -> h != null && h.matches("[a-f0-9]{64}"))
                .filter(unicas::add)
                .limit(100).toList();
        if (buscadas.isEmpty()) return List.of();
        Map<String, RutaReferencia> encontradas = rutas.buscarPorHuellas(buscadas).stream()
                .filter(r -> RutaReferencia.calcularHuella(r.getGeometriaGeoJson())
                        .equals(r.getSha256GeoJson()))
                .collect(Collectors.toMap(RutaReferencia::getSha256GeoJson, r -> r));
        return java.util.stream.IntStream.range(0, buscadas.size()).boxed()
                .filter(i -> encontradas.containsKey(buscadas.get(i)))
                .map(i -> {
                    RutaReferencia ruta = encontradas.get(buscadas.get(i));
                    return new DatosRutaAutorizada(ruta.getId(), i + 1,
                            ruta.getSha256GeoJson(), ruta.getDistanciaMetros(), ruta.getGeometriaGeoJson());
                }).toList();
    }
}
