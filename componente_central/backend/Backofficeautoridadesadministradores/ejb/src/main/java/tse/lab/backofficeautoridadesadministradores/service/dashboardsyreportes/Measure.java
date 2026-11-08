package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

// What a report shows of a KPI: its value, the one of the comparison, and how much it changed
public enum Measure {

    CURRENT("current"),
    PREVIOUS("previous"),
    GROWTH("growth"),
    DIFFERENCE("difference");

    private final String id;

    Measure(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public boolean needsComparison() {
        return this != CURRENT;
    }

    public static Measure byId(String id) {
        for (Measure measure : values()) {
            if (measure.id.equals(id)) {
                return measure;
            }
        }
        return null;
    }

    // growth is null from zero; both are null when a side has no value
    public Double of(Double current, Double previous) {
        return switch (this) {
            case CURRENT -> current;
            case PREVIOUS -> previous;
            case GROWTH -> current == null || previous == null || previous == 0 ? null : (current - previous) / previous;
            case DIFFERENCE -> current == null || previous == null ? null : current - previous;
        };
    }
}
