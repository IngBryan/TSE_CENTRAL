package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.UsuarioBackoffice;

/**
 * Un administrador o autoridad nuevo con la contraseña inicial que le definió
 * un administrador (alta o cambio de rol desde funcionario). Esa contraseña la
 * conoce otra persona: queda obligado a cambiarla en el primer ingreso.
 */
final class ContrasenaInicial {

    private ContrasenaInicial() {
    }

    /**
     * @param contrasena ya validada: el hash es costoso
     */
    static UsuarioBackoffice nuevoUsuario(RolUsuario rol, String cedula, String contrasena) {
        String hash = Contrasenas.generarHash(contrasena);
        UsuarioBackoffice usuario = switch (rol) {
            case ADMINISTRADOR -> new Administrador(cedula, hash);
            case AUTORIDAD -> new Autoridad(cedula, hash);
            default -> throw new IllegalArgumentException("No es un rol del backoffice: " + rol);
        };
        usuario.setDebeCambiarContrasena(true);
        return usuario;
    }
}
