package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.io.Serializable;

// id: el del central. idExterno: el del nodo en su sistema (balanza en la API de balanzas, gpsId en tracking).
public record DatosNodo(Long id, Long idExterno, String nombre, TipoNodo tipo) implements Serializable {
}
