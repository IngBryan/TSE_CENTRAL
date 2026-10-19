
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;
import tse.lab.model.NodoTracking;

/**
 * Centraliza la conversión de entidades {@link NodoPeriferico}
 * a objetos de transferencia {@link DatosNodo}.
 *
 * Permite reutilizar la misma representación de los nodos
 * en los servicios de administración y fiscalización automática,
 * sin exponer directamente las entidades JPA.
 */
final class DatosNodoMapper {

    private DatosNodoMapper() {
    }

    /**
     * Convierte un nodo periférico en su DTO correspondiente.
     *
     * @param nodo entidad a convertir
     * @return DTO con los datos del nodo
     * @throws IllegalArgumentException si el tipo de nodo no está soportado
     */
    static DatosNodo desde(NodoPeriferico nodo) {

        TipoNodo tipo;

        if (nodo instanceof NodoBalanza) {
            tipo = TipoNodo.BALANZA;
        } else if (nodo instanceof NodoTracking) {
            tipo = TipoNodo.TRACKING;
        } else {
            throw new IllegalArgumentException(
                "Tipo de nodo periférico no soportado");
        }

        return new DatosNodo(
            nodo.getId(),
            nodo.getIdExterno(),
            nodo.getNombre(),
            tipo,
            nodo.getUbicacion(),
            nodo.isActivo(),
            nodo.getUltimaComunicacionExitosa()
        );
    }
}
