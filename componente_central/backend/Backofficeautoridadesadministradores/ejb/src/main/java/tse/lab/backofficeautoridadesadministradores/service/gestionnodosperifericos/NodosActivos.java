package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.util.List;

// Operaciones de la vista remota (NodosActivosRemote).
public interface NodosActivos {

    // Nodos perifericos (balanzas y tracking) con activo = true, ordenados por id (lista vacia si no hay)
    List<DatosNodo> listarNodos();
}
