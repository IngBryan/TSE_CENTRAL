package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.OverloadRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.RouteDeviationRule.PruebaDesvio;
import tse.lab.fiscalizacionautomatica.ruleengine.detection.rules.TripWithoutGuideRule;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ContextoEventoSource;
import tse.lab.fiscalizacionautomatica.ruleengine.trips.ResultadoAsociacionGpsGuia;

/** Detecta todas las situaciones aplicables y adjunta la asociación temporal a la Guía. */
public class SituationDetector {
    private final VehicleFactsSource factsSource;
    private final List<SituationRule> rules;
    private final ContextoEventoSource contextoSource;

    /** Se conserva para tests y detección manual, sin asignación de casos. */
    public SituationDetector(VehicleFactsSource factsSource, List<SituationRule> rules) {
        this(factsSource, rules, null);
    }

    public SituationDetector(VehicleFactsSource factsSource, List<SituationRule> rules,
            ContextoEventoSource contextoSource) {
        this.factsSource = factsSource;
        this.rules = rules.stream().sorted(Comparator.comparing(SituationRule::situation)).toList();
        this.contextoSource = contextoSource;
    }

    public Detection detect(VehicleEvent event) throws IOException {
        Optional<VehicleFacts> facts = factsSource.find(event.licensePlate(), event.timestamp());
        if (facts.isEmpty()) {
            return new Detection(event, null, List.of());
        }
        ResultadoAsociacionGpsGuia contexto = contextoSource == null ? null : contextoSource.resolver(event);
        if (contexto != null && (contexto.estado() == ResultadoAsociacionGpsGuia.Estado.AMBIGUO
                || contexto.estado() == ResultadoAsociacionGpsGuia.Estado.NO_DETERMINABLE)) {
            // No etiquetar un evento incierto como infracción ni marcarlo como evaluado.
            throw new IOException("Asociación temporal indeterminada para " + event.licensePlate()
                    + " en " + event.timestamp() + ": " + contexto.estado());
        }
        List<Situation> situations = new ArrayList<>();
        PruebaDesvio pruebaDesvio = null;
        for (SituationRule rule : rules) {
            boolean applies;
            if (contexto != null && rule instanceof TripWithoutGuideRule) {
                applies = event instanceof tse.lab.fiscalizacionautomatica.ruleengine.model.TrackingEvent
                        && contexto.estado() == ResultadoAsociacionGpsGuia.Estado.SIN_GUIA;
            } else if (contexto != null && rule instanceof OverloadRule overload) {
                applies = contexto.estado() == ResultadoAsociacionGpsGuia.Estado.ASOCIADO
                        && overload.excedePeso(event, facts.get());
            } else if (rule instanceof RouteDeviationRule desvio) {
                var resultado = desvio.evaluar(event, facts.get());
                applies = resultado.isPresent();
                if (applies) pruebaDesvio = resultado.orElseThrow();
            } else {
                applies = rule.appliesTo(event, facts.get());
            }
            if (applies) {
                situations.add(rule.situation());
            }
        }
        return new Detection(event, facts.get(), List.copyOf(situations), contexto, pruebaDesvio);
    }
}
