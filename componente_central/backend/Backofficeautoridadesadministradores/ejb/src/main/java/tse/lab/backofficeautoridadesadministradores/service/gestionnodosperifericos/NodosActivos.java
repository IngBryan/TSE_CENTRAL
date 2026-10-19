package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.time.Instant;
import java.util.List;
import java.util.Map;

// Operaciones de la vista remota (NodosActivosRemote).
public interface NodosActivos {

    // Nodos perifericos (balanzas y tracking) con activo = true, ordenados por id (lista vacia si no hay)
    List<DatosNodo> listarNodos();

    // Ultima comunicacion exitosa de la central con cada nodo del tipo, por su idExterno. Solo avanza la
    // fecha (nunca la retrocede); los idExterno que no existen se ignoran.
    void registrarComunicaciones(TipoNodo tipo, Map<Long, Instant> ultimaPorIdExterno);
}
