package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.AsociacionTemporalGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

/** Adaptador de Fiscalización al algoritmo temporal compartido con Monitoreo. */
public final class AsociadorGpsGuia {
    private AsociadorGpsGuia() { }

    public static ResultadoAsociacionGpsGuia resolver(TrackingEvent gps, List<IntervaloGuiaGps> intervalos) {
        var resultado = AsociacionTemporalGuia.resolver(gps.licensePlate(), gps.timestamp(), intervalos);
        return switch (resultado.estado()) {
            case ASOCIADO -> ResultadoAsociacionGpsGuia.asociado(resultado.idGuia());
            case SIN_GUIA -> ResultadoAsociacionGpsGuia.sinGuia();
            case AMBIGUO -> ResultadoAsociacionGpsGuia.ambiguo();
            case NO_DETERMINABLE -> ResultadoAsociacionGpsGuia.noDeterminable();
        };
    }
}
