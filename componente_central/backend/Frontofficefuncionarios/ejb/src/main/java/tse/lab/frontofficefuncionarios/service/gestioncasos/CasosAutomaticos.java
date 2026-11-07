package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.util.List;

/** Contrato de Gestión de Casos para Fiscalización y consultas administrativas. */
public interface CasosAutomaticos {
    List<DatosCaso> casosAbiertos(String matricula);

    /** Transacción idempotente: buscar o crear caso según guía/tipo, y vincular evento una vez. */
    DatosCaso registrarEvidencia(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento);

    /** La prueba y la clave GPS se persisten en la misma transacción que el expediente. */
    DatosCaso registrarDesvio(String idGuia, EventoFiscalizado evento, PruebaDesvioRemota prueba);

    /** Devuelve null mientras falta confirmar movimiento; crea caso al confirmar desplazamiento. */
    DatosCaso registrarCirculacionSinGuia(PosicionSinGuiaRemota gps);

    /** Cierra la circulación activa si comenzó una Guía sin superponer mediciones históricas. */
    void cerrarCirculacionPorGuia(String matricula, java.time.Instant fechaInicioGuia);

    /** Notifica solo si sigue DETECTADA, conservando la correlación del evento. */
    DatosCaso notificarCaso(long idCaso, String correlationId);
}
