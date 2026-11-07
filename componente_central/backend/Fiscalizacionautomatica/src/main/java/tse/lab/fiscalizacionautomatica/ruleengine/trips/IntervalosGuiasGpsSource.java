package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;

/** Contrato remoto desacoplado de Hibernate y del frontend de Monitoreo. */
@FunctionalInterface
public interface IntervalosGuiasGpsSource {
    List<IntervaloGuiaGps> buscar(String matricula, Instant desde, Instant hasta) throws IOException;
}
