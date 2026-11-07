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

/*
 * Cambio de rol entre funcionario, administrador y autoridad. El rol nuevo
 * reemplaza a los anteriores: tras el cambio, la persona tiene uno solo.
 *
 * Entre administrador y autoridad se conserva la contraseña, porque se guarda
 * en la entidad común UsuarioBackoffice. Si no era administrador ni autoridad
 * y pasa a serlo, se pide una contraseña inicial; si no era funcionario y
 * pasa a serlo, se pide el email.
 *
 * Un funcionario que deja de serlo se marca inactivo, sin borrarse, para
 * conservar su historial (fundamentos de resolución). Si vuelve a ser
 * funcionario, se reactiva.
 *
 * Un administrador no puede cambiar su propio rol, y antes del cambio se
 * verifica, con las filas de administradores bloqueadas, que quien lo hace
 * sigue siendo administrador. Así, aunque dos administradores se quiten el
 * rol entre sí a la vez, siempre queda al menos uno.
 *
 * El cambio tiene efecto desde la próxima solicitud del usuario: la
 * autenticación del backoffice toma el rol de la base, no del token.
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
     * @throws CambioRolNoPermitidoException si ya tiene ese rol, es el propio
     *         administrador o quien lo hace ya no es administrador
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
        // Otro administrador pudo haberle quitado el rol mientras tanto
        if (!usuarios.bloquearAdministradores().contains(ciAdministrador)) {
            throw new CambioRolNoPermitidoException("Tu usuario ya no es administrador");
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
