package tse.lab.model;

/**
 * Los motivos por detencion o falta de GPS se conservan para leer registros historicos.
 * Las circulaciones nuevas solo se cierran al pasar a circular con Guia (GUIA_INICIADA).
 */
public enum MotivoFinCirculacionSinGuia {
    DETENCION_PROLONGADA, SIN_SENAL_GPS, BRECHA_TEMPORAL, GUIA_INICIADA
}
