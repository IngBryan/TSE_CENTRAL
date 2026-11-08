package tse.lab.backofficeautoridadesadministradores.service.dashboardsyreportes;

// The tables of the model the KPIs run over, by their JPA entity name
public enum Table {
    TRIP("Guia"),
    CASE("CasoFiscalizacion"),
    COMPANY("Empresa"),
    VEHICLE("Vehiculo");

    private final String entity;

    Table(String entity) {
        this.entity = entity;
    }

    public String entity() {
        return entity;
    }
}
