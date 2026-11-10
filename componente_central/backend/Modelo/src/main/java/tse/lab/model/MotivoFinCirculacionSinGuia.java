package tse.lab.model;

/**
 * Los motivos por detencion o falta de GPS se conservan para leer registros historicos.
 * Las circulaciones nuevas se cierran al circular con Guia o al iniciar otro caso tras una resolución.
 */
public enum MotivoFinCirculacionSinGuia {
    DETENCION_PROLONGADA, SIN_SENAL_GPS, BRECHA_TEMPORAL, GUIA_INICIADA, CASO_RESUELTO
}
