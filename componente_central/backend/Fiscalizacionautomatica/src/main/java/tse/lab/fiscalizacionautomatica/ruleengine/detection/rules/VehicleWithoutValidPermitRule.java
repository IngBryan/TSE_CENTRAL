package tse.lab.fiscalizacionautomatica.ruleengine.detection.rules;

import java.io.IOException;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.Situation;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.SituationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.VehicleFacts;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/** Permiso o ITV ausente o sin vigencia; aplica a GPS y pesajes, con o sin Guía. */
public class VehicleWithoutValidPermitRule implements SituationRule {
    @Override
    public Situation situation() { return Situation.VEHICLE_WITHOUT_VALID_PERMIT; }

    @Override
    public boolean appliesTo(VehicleEvent event, VehicleFacts facts) throws IOException {
        var prueba = facts.vehicle().getHabilitacion();
        if (prueba == null || !event.timestamp().equals(prueba.getFechaHora())
                || !event.licensePlate().equals(prueba.getMatricula())) {
            // Una respuesta incompleta no equivale a documentos ausentes: reintentar el evento.
            throw new IOException("No se recibió la habilitación del vehículo para este evento");
        }
        return prueba.sinHabilitacion();
    }
}
