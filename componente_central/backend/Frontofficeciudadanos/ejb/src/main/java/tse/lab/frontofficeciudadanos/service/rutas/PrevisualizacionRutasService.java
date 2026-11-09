package tse.lab.frontofficeciudadanos.service.rutas;

import java.util.Comparator;
import java.util.List;
import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.model.Localidad;
import tse.lab.model.RutaReferencia;

/** La unica ruta admitida es la de menor duracion devuelta por OSRM. */
@Stateless
@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
public class PrevisualizacionRutasService {
    public record RutaVista(int alternativa, String sha256, double distanciaMetros,
            double duracionSegundos, String geoJson) { }
    public record Previsualizacion(Long origenId, Long destinoId, String previsualizacionId,
            List<RutaVista> rutas) { }

    @EJB private LocalidadDAO localidades;
    @EJB private OsrmRutasClient osrm;

    public Previsualizacion calcular(Long origenId, Long destinoId) {
        if (origenId == null || destinoId == null || origenId.equals(destinoId)) {
            throw new RutasException(400, "localidades_invalidas", "Origen y destino deben ser diferentes");
        }
        Localidad origen = localidades.buscarPorId(origenId);
        Localidad destino = localidades.buscarPorId(destinoId);
        if (origen == null || destino == null) {
            throw new RutasException(400, "localidades_invalidas", "Localidad inexistente");
        }
        return seleccionar(origenId, destinoId, osrm.obtener(origen, destino));
    }

    public static Previsualizacion seleccionar(Long origenId, Long destinoId,
            List<OsrmRutasClient.Candidata> candidatas) {
        if (candidatas == null || candidatas.isEmpty()) {
            throw new RutasException(422, "sin_ruta_vial", "No se obtuvieron rutas");
        }
        List<OsrmRutasClient.Candidata> seleccionadas = candidatas.stream()
                .sorted(Comparator.comparingDouble(OsrmRutasClient.Candidata::duracionSegundos))
                .limit(1).toList();
        List<RutaVista> rutas = java.util.stream.IntStream.range(0, seleccionadas.size())
                .mapToObj(i -> {
                    var r = seleccionadas.get(i);
                    return new RutaVista(i + 1, RutaReferencia.calcularHuella(r.geoJsonCanonico()),
                            r.distanciaMetros(), r.duracionSegundos(), r.geoJsonCanonico());
                }).toList();
        String contenido = origenId + ":" + destinoId + ":" + rutas.stream()
                .map(RutaVista::sha256).collect(java.util.stream.Collectors.joining(":"));
        return new Previsualizacion(origenId, destinoId, RutaReferencia.calcularHuella(contenido), rutas);
    }
}
