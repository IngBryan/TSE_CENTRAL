package tse.lab.fiscalizacionautomatica.ruleengine.detection;

/**
 * Possible non-compliance situations, in priority order (the declaration order is the priority).
 * Without an active trip only TRIP_WITHOUT_GUIDE and VEHICLE_WITHOUT_VALID_PERMIT can apply.
 */
public enum Situation {
    TRIP_WITHOUT_GUIDE("VIAJE_SIN_GUIA"),
    OVERLOAD("EXCESO_CARGA"),
    VEHICLE_WITHOUT_VALID_PERMIT("VEHICULO_SIN_HABILITACION"),
    ROUTE_DEVIATION("DESVIO_RUTA");

    private final String caseType;

    Situation(String caseType) {
        this.caseType = caseType;
    }

    /** @return the type of the fiscalization case it creates (CasoFiscalizacion.tipoCaso) */
    public String caseType() {
        return caseType;
    }
}
