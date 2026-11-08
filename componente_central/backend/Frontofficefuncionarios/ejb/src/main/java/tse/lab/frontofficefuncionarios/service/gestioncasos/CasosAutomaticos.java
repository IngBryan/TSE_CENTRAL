package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.util.List;
import java.time.Instant;
import tse.lab.model.EvidenciaHabilitacion;

/** Contrato de Gestión de Casos para Fiscalización y consultas administrativas. */
public interface CasosAutomaticos {
    List<DatosCaso> casosAbiertos(String matricula);

    /** Transacción idempotente: buscar o crear caso según guía/tipo, y vincular evento una vez. */
    DatosCaso registrarEvidencia(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento);

    /** Persiste la evaluación documental junto al evento, sin reconstruirla con datos posteriores. */
    DatosCaso registrarHabilitacion(String idGuia, EventoFiscalizado evento,
            EvidenciaHabilitacion prueba);

    /** La prueba y la clave GPS se persisten en la misma transacción que el expediente. */
    DatosCaso registrarDesvio(String idGuia, EventoFiscalizado evento, PruebaDesvioRemota prueba);

    /** Devuelve null mientras falta confirmar movimiento; crea caso al confirmar desplazamiento. */
    DatosCaso registrarCirculacionSinGuia(PosicionSinGuiaRemota gps);

    /** Cierra la circulación activa si comenzó una Guía sin superponer mediciones históricas. */
    void cerrarCirculacionPorGuia(String matricula, Instant fechaInicioGuia);

    /** Notifica solo si sigue DETECTADA, conservando la correlación del evento. */
    DatosCaso notificarCaso(long idCaso, String correlationId);
}
