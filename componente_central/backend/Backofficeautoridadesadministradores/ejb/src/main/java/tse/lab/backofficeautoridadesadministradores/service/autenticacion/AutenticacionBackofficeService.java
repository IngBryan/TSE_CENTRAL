
package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

import java.util.Optional;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioBackofficeDAO;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.UsuarioBackoffice;

/**
 * Valida las credenciales de los usuarios del Backoffice.
 */
@Stateless
public class AutenticacionBackofficeService {

    @EJB
    private UsuarioBackofficeDAO usuarios;

    /**
     * Autentica un usuario por cédula y contraseña.
     *
     * @return identidad autenticada, o vacío si las
     *         credenciales no son válidas.
     */
    public Optional<IdentidadBackoffice> autenticar(
            String ci,
            String contrasena) {

        if (ci == null || ci.isBlank()
                || contrasena == null
                || contrasena.isEmpty()) {
            return Optional.empty();
        }

        String ciNormalizada = ci.strip();

        UsuarioBackoffice usuario =
                usuarios.buscarPorCi(ciNormalizada);

        if (usuario == null) {
            return Optional.empty();
        }

        if (!Contrasenas.verificar(
                contrasena,
                usuario.getContrasena())) {
            return Optional.empty();
        }

        return rolDe(usuario)
                .map(rol -> new IdentidadBackoffice(usuario.getCi(), rol));
    }

    /**
     * El rol vigente de un usuario ya autenticado por su token. Un
     * administrador puede haberle cambiado el rol (o quitado) después de
     * emitido el token: este es el que vale.
     *
     * @return ADMINISTRADOR, AUTORIDAD o vacío si ya no es usuario del backoffice
     */
    public Optional<String> rolActual(String ci) {
        if (ci == null || ci.isBlank()) {
            return Optional.empty();
        }
        return rolDe(usuarios.buscarPorCi(ci));
    }

    private static Optional<String> rolDe(UsuarioBackoffice usuario) {
        if (usuario instanceof Administrador) {
            return Optional.of("ADMINISTRADOR");
        }
        if (usuario instanceof Autoridad) {
            return Optional.of("AUTORIDAD");
        }
        return Optional.empty();
    }
}
