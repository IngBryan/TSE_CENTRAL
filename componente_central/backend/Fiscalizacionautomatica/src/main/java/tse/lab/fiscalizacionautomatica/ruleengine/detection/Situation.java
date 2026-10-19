package tse.lab.fiscalizacionautomatica.ruleengine.detection;

import tse.lab.frontofficefuncionarios.service.gestioncasos.TipoCasoAutomatico;

/**
 * Possible non-compliance situations, in priority order (the declaration order is the priority).
 * Without an active trip only TRIP_WITHOUT_GUIDE and VEHICLE_WITHOUT_VALID_PERMIT can apply.
 */
public enum Situation {
    TRIP_WITHOUT_GUIDE(TipoCasoAutomatico.VIAJE_SIN_GUIA),
    OVERLOAD(TipoCasoAutomatico.EXCESO_CARGA),
    VEHICLE_WITHOUT_VALID_PERMIT(TipoCasoAutomatico.VEHICULO_SIN_HABILITACION),
    ROUTE_DEVIATION(TipoCasoAutomatico.DESVIO_RUTA);

    private final TipoCasoAutomatico caseType;

    Situation(TipoCasoAutomatico caseType) {
        this.caseType = caseType;
    }

    /** @return the type of the fiscalization case it creates (CasoFiscalizacion.tipoCaso) */
    public TipoCasoAutomatico caseType() {
        return caseType;
    }

    /** Whether the case belongs to this situation (DatosCaso carries the type as text). */
    public boolean isTypeOf(String caseType) {
        return this.caseType.name().equals(caseType);
    }
}
