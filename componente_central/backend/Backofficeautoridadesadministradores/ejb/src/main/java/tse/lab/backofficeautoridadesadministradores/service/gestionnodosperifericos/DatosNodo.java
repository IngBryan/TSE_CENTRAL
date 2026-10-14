package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.io.Serializable;

public record DatosNodo(Long id, String nombre, TipoNodo tipo) implements Serializable {
}
