package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tse.lab.fiscalizacionautomatica.ruleengine.model.VehicleEvent;

/**
 * Finds the situations that apply to a vehicle event. It doesn't create cases: that step (which also
 * skips the situations that already have an open case of the vehicle) comes later.
 */
public class SituationDetector {

    private final VehicleFactsSource factsSource;
    private final List<SituationRule> rules;

    /** The rules are evaluated in priority order, whatever their order in the list. */
    public SituationDetector(VehicleFactsSource factsSource, List<SituationRule> rules) {
        this.factsSource = factsSource;
        this.rules = rules.stream().sorted(Comparator.comparing(SituationRule::situation)).toList();
    }

    /**
     * @return the situations that apply, in priority order; empty if none does or if the vehicle is not
     *         registered in the central (there is no company to notify)
     * @throws IOException if the vehicle facts can't be fetched: the event must be evaluated again later
     */
    public List<Situation> detect(VehicleEvent event) throws IOException {
        Optional<VehicleFacts> facts = factsSource.find(event.licensePlate());
        if (facts.isEmpty()) {
            System.out.println("Vehicle " + event.licensePlate() + " is not registered: no situations");
            return List.of();
        }
        return rules.stream()
                .filter(rule -> rule.appliesTo(event, facts.get()))
                .map(SituationRule::situation)
                .toList();
    }
}
