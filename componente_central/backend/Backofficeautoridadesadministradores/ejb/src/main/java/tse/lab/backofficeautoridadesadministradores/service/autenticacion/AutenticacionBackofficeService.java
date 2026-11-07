
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

        String rol;

        if (usuario instanceof Administrador) {
            rol = "ADMINISTRADOR";
        } else if (usuario instanceof Autoridad) {
            rol = "AUTORIDAD";
        } else {
            return Optional.empty();
        }

        return Optional.of(
                new IdentidadBackoffice(
                        usuario.getCi(), rol));
    }
}
