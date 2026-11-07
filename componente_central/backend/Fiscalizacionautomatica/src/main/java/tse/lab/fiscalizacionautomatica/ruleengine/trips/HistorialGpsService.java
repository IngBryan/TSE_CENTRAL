package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import tse.lab.fiscalizacionautomatica.ruleengine.data.TrackingEventDAO;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

/**
 * Lectura sin efectos laterales para la futura API de Monitoreo y la regla de desvío.
 * Resuelve cada punto contra intervalos históricos; no se basa en el estado ACTUAL de
 * la Guía. No se usa para marcar eventos como detectados ni para consumir RabbitMQ.
 */
public final class HistorialGpsService {
    private static final int MAX_PUNTOS = 5_000;

    private final TrackingEventDAO dao;
    private final IntervalosGuiasGpsSource intervalos;

    public HistorialGpsService(TrackingEventDAO dao, IntervalosGuiasGpsSource intervalos) {
        this.dao = Objects.requireNonNull(dao, "dao");
        this.intervalos = Objects.requireNonNull(intervalos, "intervalos");
    }

    /**
     * Consulta [desde,hasta), máximo 5000 eventos candidatos por llamada. El límite
     * se aplica antes del filtrado para no convertir una consulta en un escaneo masivo.
     * El consumidor puede avanzar el cursor de tiempo cuando necesite más posiciones.
     */
    public List<TrackingEvent> obtenerPorGuia(String idGuia, String matricula, Instant desde, Instant hasta,
            int limite) throws IOException {
        if (idGuia == null || idGuia.isBlank() || matricula == null || matricula.isBlank()
                || desde == null || hasta == null || !desde.isBefore(hasta)
                || limite < 1 || limite > MAX_PUNTOS) {
            throw new IllegalArgumentException("Consulta GPS invalida");
        }
        List<IntervaloGuiaGps> ventanas = intervalos.buscar(matricula, desde, hasta);
        if (ventanas.stream().noneMatch(i -> idGuia.equals(i.getIdGuia()))) {
            return List.of();
        }
        return dao.findByLicensePlateBetween(matricula, desde, hasta, limite).stream()
                .filter(e -> AsociadorGpsGuia.resolver(e, ventanas).correspondeA(idGuia))
                .toList();
    }
}
