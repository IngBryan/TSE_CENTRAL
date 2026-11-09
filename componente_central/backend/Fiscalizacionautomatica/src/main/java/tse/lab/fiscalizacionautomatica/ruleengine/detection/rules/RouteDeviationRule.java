package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.routes.CorredorRutas;
import tse.lab.fiscalizacionautomatica.ruleengine.routes.RutasAutorizadasSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.AsociadorGpsGuia;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.IntervalosGuiasGpsSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;

/**
 * Confirma un desvío solo cuando dos muestras temporalmente consecutivas de la MISMA Guía
 * superan la distancia máxima a TODAS las rutas permitidas y están separadas como máximo
 * por la ventana de confirmación. Se usa la fecha de medición del GPS, nunca receivedAt.
 * La evaluación devuelve además una instantánea probatoria para el expediente (4C).
 */
public final class RouteDeviationRule implements SituationRule {
    public static final double UMBRAL_METROS = 2_000;
    public static final Duration VENTANA = Duration.ofSeconds(30);
    private static final int MAX_CANDIDATOS = 1_000;

    private final IntervalosGuiasGpsSource intervalos;
    private final RutasAutorizadasSource rutas;
    private final TrackingEventDAO posiciones;
    private final double umbralMetros;
    private final Duration ventanaConfirmacion;

    public RouteDeviationRule(IntervalosGuiasGpsSource intervalos, RutasAutorizadasSource rutas,
            TrackingEventDAO posiciones) {
        this(intervalos, rutas, posiciones, UMBRAL_METROS, VENTANA);
    }

    public RouteDeviationRule(IntervalosGuiasGpsSource intervalos, RutasAutorizadasSource rutas,
            TrackingEventDAO posiciones, double umbralMetros, Duration ventana) {
        this.intervalos = Objects.requireNonNull(intervalos);
        this.rutas = Objects.requireNonNull(rutas);
        this.posiciones = Objects.requireNonNull(posiciones);
        if (!Double.isFinite(umbralMetros) || umbralMetros <= 0 || ventana == null || ventana.isNegative()
                || ventana.isZero()) {
            throw new IllegalArgumentException("Umbral y ventana inválidos");
        }
        this.umbralMetros = umbralMetros;
        this.ventanaConfirmacion = ventana;
    }

    @Override
    public Situation situation() {
        return Situation.ROUTE_DEVIATION;
    }

    public Optional<PruebaDesvio> evaluar(VehicleEvent event, VehicleFacts facts,
            tse.lab.reglas.ConfiguracionReglas configuracion) throws IOException {
        return new RouteDeviationRule(intervalos, rutas, posiciones, configuracion.getDistanciaDesvioMetros(),
                Duration.ofSeconds(configuracion.getVentanaDesvioSegundos())).evaluar(event, facts);
    }

    /** Evidencia reproducible del par utilizado y de las rutas consultadas. */
    public record PruebaDesvio(TrackingEvent confirmante, TrackingEvent pareja,
            double distanciaConfirmanteMetros, double distanciaParejaMetros,
            double umbralMetros, long ventanaMaximaSegundos, List<String> hashesRutas) { }

    @Override
    public boolean appliesTo(VehicleEvent event, VehicleFacts facts) throws IOException {
        return evaluar(event, facts).isPresent();
    }

    public Optional<PruebaDesvio> evaluar(VehicleEvent event, VehicleFacts facts) throws IOException {
        if (!(event instanceof TrackingEvent gps)) {
            return Optional.empty(); // Los pesajes nunca son evidencia espacial de desvío.
        }
        Instant desde = gps.timestamp().minus(ventanaConfirmacion);
        // [desde,hasta) incluye la muestra exactamente en timestamp+ventana.
        Instant hasta = gps.timestamp().plus(ventanaConfirmacion).plusMillis(1);
        List<IntervaloGuiaGps> ventanas = intervalos.buscar(gps.licensePlate(), desde, hasta);
        var asociacion = AsociadorGpsGuia.resolver(gps, ventanas);
        if (asociacion.estado() != ResultadoAsociacionGpsGuia.Estado.ASOCIADO) {
            return Optional.empty(); // Sin Guía, ambiguo o incertidumbre: no hay ruta fiscalizable.
        }
        String idGuia = asociacion.idGuia();
        List<DatosRutaAutorizada> autorizadas = rutas.obtenerPorGuia(idGuia);
        if (autorizadas == null || autorizadas.isEmpty()) {
            return Optional.empty(); // No se inventa un corredor de referencia.
        }
        CorredorRutas corredor = new CorredorRutas(autorizadas);
        double distanciaPrincipal = corredor.distanciaMinimaMetros(gps.latitude(), gps.longitude());
        if (distanciaPrincipal <= umbralMetros) {
            return Optional.empty();
        }

        List<TrackingEvent> candidatos = posiciones.findByLicensePlateBetween(gps.licensePlate(), desde, hasta,
                MAX_CANDIDATOS);
        if (candidatos.size() >= MAX_CANDIDATOS) {
            return Optional.empty(); // No confirmar con datos incompletos por límite de consulta.
        }
        List<TrackingEvent> ordenadas = new ArrayList<>(candidatos);
        // Un evento recién recibido puede no aparecer aún en una implementación del DAO.
        ordenadas.add(gps);
        ordenadas = ordenadas.stream()
                .filter(p -> p.licensePlate().equals(gps.licensePlate()))
                .collect(Collectors.toMap(TrackingEvent::timestamp, p -> p, (a, b) -> a))
                .values().stream().sorted(Comparator.comparing(TrackingEvent::timestamp)).toList();

        int indice = -1;
        for (int i = 0; i < ordenadas.size(); i++) {
            if (ordenadas.get(i).timestamp().equals(gps.timestamp())) {
                indice = i;
                break;
            }
        }
        if (indice < 0) {
            return Optional.empty();
        }
        // Solo vecinos inmediatos: un GPS dentro del corredor entre dos muestras externas
        // impide contar esas dos muestras como consecutivas.
        TrackingEvent pareja = null;
        if (indice > 0 && confirmarPar(gps, ordenadas.get(indice - 1), idGuia, ventanas, corredor)) {
            pareja = ordenadas.get(indice - 1);
        } else if (indice + 1 < ordenadas.size()
                && confirmarPar(gps, ordenadas.get(indice + 1), idGuia, ventanas, corredor)) {
            pareja = ordenadas.get(indice + 1);
        }
        if (pareja == null) return Optional.empty();
        List<String> hashes = autorizadas.stream().map(DatosRutaAutorizada::getSha256).toList();
        if (hashes.stream().anyMatch(h -> h == null || h.isBlank())) {
            throw new IOException("La ruta autorizada no tiene hash de referencia");
        }
        return Optional.of(new PruebaDesvio(gps, pareja, distanciaPrincipal,
                corredor.distanciaMinimaMetros(pareja.latitude(), pareja.longitude()),
                umbralMetros, ventanaConfirmacion.toSeconds(), List.copyOf(hashes)));
    }

    private boolean confirmarPar(TrackingEvent actual, TrackingEvent otra, String idGuia,
            List<IntervaloGuiaGps> ventanas, CorredorRutas corredor) {
        Duration diferencia = Duration.between(actual.timestamp(), otra.timestamp()).abs();
        return !diferencia.isZero() && diferencia.compareTo(ventanaConfirmacion) <= 0
                && AsociadorGpsGuia.resolver(otra, ventanas).correspondeA(idGuia)
                && corredor.distanciaMinimaMetros(otra.latitude(), otra.longitude()) > umbralMetros;
    }
}
