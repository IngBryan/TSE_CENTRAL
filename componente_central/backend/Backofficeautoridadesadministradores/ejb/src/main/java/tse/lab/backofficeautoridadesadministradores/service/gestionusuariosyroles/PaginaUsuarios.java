package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.List;

/**
 * Una página del listado de usuarios.
 *
 * pagina empieza en 1; totalPaginas es 0 si no hay usuarios.
 */
public record PaginaUsuarios(
        List<DatosUsuario> usuarios,
        int pagina,
        int tamanio,
        long total,
        int totalPaginas) {
}
