package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioBackofficeDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.model.UsuarioBackoffice;

/**
 * Cambio de la contraseña propia de un administrador o una autoridad ya
 * logueado. Pide la contraseña actual: un token robado no alcanza para
 * quedarse con la cuenta.
 *
 * Los tokens ya emitidos siguen valiendo hasta que vencen: no hay estado en
 * el servidor para revocarlos.
 */
@Stateless
public class CambioContrasenaService {

    private static final Logger LOG = Logger.getLogger(CambioContrasenaService.class.getName());

    private static final String INCORRECTA = "La contraseña actual no es correcta";

    @EJB
    private UsuarioBackofficeDAO usuarios;

    /**
     * Valida la contraseña nueva antes de verificar la actual, porque el hash
     * es costoso.
     *
     * @param ci el usuario logueado, tomado del token
     * @throws DatosUsuarioInvalidosException si la nueva no cumple el largo o
     *         es igual a la actual
     * @throws ContrasenaActualIncorrectaException si la actual no coincide
     * @throws UsuarioNoEncontradoException si la cédula ya no es usuario del
     *         backoffice
     */
    public void cambiar(String ci, String contrasenaActual, String contrasenaNueva) {
        ValidacionUsuario.contrasena(contrasenaNueva);

        if (contrasenaActual == null || contrasenaActual.isEmpty()) {
            throw new ContrasenaActualIncorrectaException(INCORRECTA);
        }
        if (contrasenaNueva.equals(contrasenaActual)) {
            throw new DatosUsuarioInvalidosException("La contraseña nueva tiene que ser distinta de la actual");
        }

        UsuarioBackoffice usuario = ci == null ? null : usuarios.buscarPorCi(ci);
        if (usuario == null) {
            throw new UsuarioNoEncontradoException("La cédula no es usuario del backoffice");
        }

        String hashAnterior = usuario.getContrasena();
        if (!Contrasenas.verificar(contrasenaActual, hashAnterior)) {
            LogBackoffice.evento("usuario.contrasena.rechazada")
                    .nivel(Level.WARNING)
                    .usuario(ci)
                    .escribirEn(LOG);
            throw new ContrasenaActualIncorrectaException(INCORRECTA);
        }

        // Si otro cambio ganó la carrera, la contraseña actual que verificamos ya no es la vigente
        if (!usuarios.actualizarContrasena(ci, hashAnterior, Contrasenas.generarHash(contrasenaNueva))) {
            throw new ContrasenaActualIncorrectaException(INCORRECTA);
        }

        LOG.info(() -> LogBackoffice.evento("usuario.contrasena.cambio")
                .usuario(ci)
                .toString());
    }
}
