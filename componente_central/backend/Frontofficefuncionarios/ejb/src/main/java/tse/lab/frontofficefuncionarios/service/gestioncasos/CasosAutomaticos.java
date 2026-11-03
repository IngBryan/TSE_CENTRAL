package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.util.List;

// Operaciones de la vista remota (CasosAutomaticosRemote). Son operaciones simples sobre los casos: que caso
// crear o a cual sumar un evento lo decide la fiscalizacion automatica (jerarquia de situaciones).
public interface CasosAutomaticos {

    // Casos abiertos (DETECTADA o NOTIFICADA) del vehiculo
    List<DatosCaso> casosAbiertos(String matricula);

    // El caso que ya tiene ese evento como evidencia; null si ninguno
    DatosCaso casoDelEvento(EventoFiscalizado evento);

    // Crea un caso DETECTADA con el evento como evidencia y lo registra en la auditoria (usuario SISTEMA).
    // idGuia: null si el caso no tiene guia (Viaje sin guia). Falla si falta el tipo, si el vehiculo o la guia
    // no existen, o si el vehiculo ya tiene un caso abierto de ese tipo (indice unico parcial de la base).
    DatosCaso crearCaso(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento);

    // Suma el evento como evidencia del caso
    void agregarEvidencia(long idCaso, EventoFiscalizado evento);

    // Notifica el caso a la empresa: pasa de DETECTADA a NOTIFICADA (auditoria con usuario SISTEMA).
    // No hace nada si el caso ya no esta DETECTADA.
    DatosCaso notificarCaso(long idCaso, String correlationId);
}
