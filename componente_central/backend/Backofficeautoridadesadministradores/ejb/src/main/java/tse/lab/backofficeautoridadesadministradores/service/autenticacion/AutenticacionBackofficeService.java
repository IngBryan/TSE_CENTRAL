
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
     * @return la cuenta autenticada (con su rol y si tiene pendiente el cambio
     *         de contraseña), o vacío si las credenciales no son válidas.
     */
    public Optional<CuentaBackoffice> autenticar(
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
                .map(rol -> new CuentaBackoffice(
                        usuario.getCi(), rol, usuario.isDebeCambiarContrasena()));
    }

    /**
     * La cuenta vigente de un usuario ya autenticado por su token. Un
     * administrador puede haberle cambiado el rol (o quitado), y el usuario
     * puede haber hecho el cambio de contraseña obligatorio, después de
     * emitido el token: esto es lo que vale.
     *
     * @return la cuenta, o vacío si ya no es usuario del backoffice
     */
    public Optional<CuentaBackoffice> cuentaActual(String ci) {
        if (ci == null || ci.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(usuarios.buscarCuenta(ci));
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
