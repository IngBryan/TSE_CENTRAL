package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.Funcionario;

/**
 * Cambio de rol entre funcionario, administrador y autoridad. El rol nuevo
 * reemplaza al anterior: la persona queda con uno solo de los tres.
 *
 * <ul>
 * <li>Administrador y autoridad comparten la contraseña: pasar de uno al
 * otro la conserva.</li>
 * <li>Pasar a administrador o autoridad sin serlo pide una contraseña
 * inicial; pasar a funcionario sin serlo pide el email.</li>
 * <li>Un funcionario que deja de serlo no se borra: queda inactivo, con su
 * historial (fundamentos de resolución). Si vuelve a ser funcionario se
 * reactiva.</li>
 * <li>Un administrador no puede cambiar su propio rol: así siempre queda al
 * menos un administrador.</li>
 * </ul>
 *
 * El cambio rige en el siguiente pedido: la autenticación del backoffice
 * verifica el rol en la base en cada pedido, no el del token.
 */
@Stateless
public class CambioRolService {

    private static final Logger LOG = Logger.getLogger(CambioRolService.class.getName());

    @EJB
    private UsuarioDAO usuarios;

    public record RolCambiado(String ci, RolUsuario rol) {
    }

    /**
     * @param ciAdministrador quién hace el cambio
     * @param rol el rol nuevo: ADMINISTRADOR, AUTORIDAD o FUNCIONARIO
     * @param contrasena obligatoria si pasa a administrador o autoridad sin serlo
     * @param email obligatorio si pasa a funcionario sin serlo
     * @throws DatosUsuarioInvalidosException si algún dato no es válido
     * @throws UsuarioNoEncontradoException si no es funcionario, administrador ni autoridad
     * @throws CambioRolNoPermitidoException si ya tiene ese rol o es el propio administrador
     */
    public RolCambiado cambiar(
            String ciAdministrador,
            String ci,
            String rol,
            String contrasena,
            String email) {

        String cedula = ValidacionUsuario.cedula(ci);
        RolUsuario nuevo = ValidacionUsuario.rolInterno(rol);

        if (cedula.equals(ciAdministrador)) {
            throw new CambioRolNoPermitidoException("No podés cambiar tu propio rol");
        }

        RolUsuario rolBackoffice = usuarios.rolBackoffice(cedula);
        Funcionario funcionario = usuarios.buscarFuncionario(cedula);
        boolean esFuncionario = funcionario != null && funcionario.isActivo();

        List<RolUsuario> anteriores = new ArrayList<>();
        if (rolBackoffice != null) {
            anteriores.add(rolBackoffice);
        }
        if (esFuncionario) {
            anteriores.add(RolUsuario.FUNCIONARIO);
        }

        if (anteriores.isEmpty()) {
            throw new UsuarioNoEncontradoException(
                    "La cédula " + cedula + " no es funcionario, administrador ni autoridad");
        }
        if (anteriores.equals(List.of(nuevo))) {
            throw new CambioRolNoPermitidoException("La cédula " + cedula + " ya tiene ese rol");
        }

        if (nuevo == RolUsuario.FUNCIONARIO) {
            pasarAFuncionario(cedula, funcionario, esFuncionario, email, rolBackoffice);
        } else {
            pasarABackoffice(cedula, nuevo, rolBackoffice, contrasena, funcionario, esFuncionario);
        }

        LOG.info(() -> LogBackoffice.evento("usuario.rol_cambiado")
                .usuario(ciAdministrador)
                .campo("ci", cedula)
                .campo("rolesAnteriores", anteriores)
                .campo("rolNuevo", nuevo)
                .toString());

        return new RolCambiado(cedula, nuevo);
    }

    private void pasarAFuncionario(
            String cedula,
            Funcionario funcionario,
            boolean esFuncionario,
            String email,
            RolUsuario rolBackoffice) {

        if (!esFuncionario) {
            String correo = ValidacionUsuario.email(email);
            if (funcionario == null) {
                usuarios.guardar(new Funcionario(cedula, correo));
            } else {
                funcionario.setActivo(true);
                funcionario.setEmail(correo);
            }
        }

        if (rolBackoffice != null) {
            usuarios.eliminarUsuarioBackoffice(cedula, rolBackoffice);
        }
    }

    private void pasarABackoffice(
            String cedula,
            RolUsuario nuevo,
            RolUsuario rolBackoffice,
            String contrasena,
            Funcionario funcionario,
            boolean esFuncionario) {

        if (rolBackoffice == null) {
            // Se valida antes de calcular el hash, que es costoso
            ValidacionUsuario.contrasena(contrasena);
            String hash = Contrasenas.generarHash(contrasena);
            usuarios.guardar(nuevo == RolUsuario.ADMINISTRADOR
                    ? new Administrador(cedula, hash)
                    : new Autoridad(cedula, hash));
        } else if (rolBackoffice != nuevo) {
            usuarios.cambiarRolBackoffice(cedula, rolBackoffice, nuevo);
        }

        if (esFuncionario) {
            funcionario.setActivo(false);
        }
    }
}
