
package tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos;

import java.util.List;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.NodoPerifericoDAO;
import tse.lab.model.NodoBalanza;
import tse.lab.model.NodoPeriferico;
import tse.lab.model.NodoTracking;

/**
 * Implementa las operaciones de negocio para la gestión
 * administrativa de nodos periféricos.
 *
 * Coordina la validación, registro y consulta de nodos,
 * delegando la persistencia en NodoPerifericoDAO.
 */
@Stateless
public class GestionNodosPerifericosService {

    @EJB
    private NodoPerifericoDAO nodos;

    /**
     * Registra un nodo periférico con estado inicial activo.
     *
     * @param tipo tipo de nodo a registrar
     * @param nombre nombre del nodo
     * @param ubicacion ubicación del nodo
     * @param idExterno identificador en el sistema de origen
     * @return datos del nodo registrado
     * @throws DatosNodoInvalidosException si los datos son inválidos
     * @throws NodoDuplicadoException si el identificador ya existe
     *         para el mismo tipo
     */
    public DatosNodo registrar(
            TipoNodo tipo,
            String nombre,
            String ubicacion,
            Long idExterno) {

        // Validar datos obligatorios.
        if (tipo == null) {
            throw new DatosNodoInvalidosException(
                    "El tipo de nodo es obligatorio");
        }

        nombre = validarTexto(nombre, "nombre", 100);
        ubicacion = validarTexto(ubicacion, "ubicacion", 200);

        if (idExterno == null) {
            throw new DatosNodoInvalidosException(
                    "El identificador externo es obligatorio");
        }

        // Crear la entidad del subtipo solicitado.
        NodoPeriferico nodo = switch (tipo) {
            case BALANZA ->
                new NodoBalanza(nombre, ubicacion, idExterno);
            case TRACKING ->
                new NodoTracking(nombre, ubicacion, idExterno);
        };

        // El identificador externo es único dentro de cada tipo.
        if (nodos.existePorTipoEIdExterno(
                nodo.getClass(), idExterno)) {

            throw new NodoDuplicadoException(tipo, idExterno);
        }

        // Persistir el nodo.
        nodos.guardar(nodo);

        // Convertir la entidad JPA al DTO compartido.
        return DatosNodoMapper.desde(nodo);
    }

    /**
     * Obtiene todos los nodos periféricos registrados,
     * independientemente de su estado.
     *
     * @return listado de nodos periféricos
     */
    public List<DatosNodo> listarTodos() {
        return nodos.listarTodos()
                .stream()
                .map(DatosNodoMapper::desde)
                .toList();
    }

    /**
     * Valida y normaliza un campo de texto obligatorio.
     *
     * @param valor texto recibido
     * @param campo nombre del campo para los mensajes de error
     * @param longitudMaxima longitud máxima permitida
     * @return texto sin espacios al principio ni al final
     * @throws DatosNodoInvalidosException si el valor es inválido
     */
    private static String validarTexto(
            String valor,
            String campo,
            int longitudMaxima) {

        if (valor == null || valor.isBlank()) {
            throw new DatosNodoInvalidosException(
                    "El campo " + campo + " es obligatorio");
        }

        String normalizado = valor.strip();

        if (normalizado.length() > longitudMaxima) {
            throw new DatosNodoInvalidosException(
                    "El campo " + campo
                            + " supera los "
                            + longitudMaxima + " caracteres");
        }

        return normalizado;
    }
}
