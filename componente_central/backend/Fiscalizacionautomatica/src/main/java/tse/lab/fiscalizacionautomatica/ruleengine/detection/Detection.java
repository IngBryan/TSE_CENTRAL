package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.util.List;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;

/** Resultado de detección y contexto temporal comprobado del evento. */
public record Detection(VehicleEvent event, VehicleFacts facts, List<Situation> situations,
        ResultadoAsociacionGpsGuia contexto, PruebaDesvio pruebaDesvio) {
    public Detection(VehicleEvent event, VehicleFacts facts, List<Situation> situations,
            ResultadoAsociacionGpsGuia contexto) {
        this(event, facts, situations, contexto, null);
    }
    /** Para detectores sin fuente temporal (pruebas y modo manual; no asignar casos). */
    public Detection(VehicleEvent event, VehicleFacts facts, List<Situation> situations) {
        this(event, facts, situations, null, null);
    }
}
