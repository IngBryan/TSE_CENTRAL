package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.util.List;

/** Contrato de Gestión de Casos para Fiscalización y consultas administrativas. */
public interface CasosAutomaticos {
    List<DatosCaso> casosAbiertos(String matricula);

    /** Transacción idempotente: buscar o crear caso según guía/tipo, y vincular evento una vez. */
    DatosCaso registrarEvidencia(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento);

    DatosCaso notificarCaso(long idCaso);
}
