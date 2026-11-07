package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.model.Funcionario;

/*
 * Baja de un usuario interno: se le quitan los roles de funcionario,
 * administrador y autoridad que tenga. Los roles de chofer y responsable no
 * se modifican, porque tienen su propio ciclo de vida.
 *
 * Si es administrador o autoridad, el registro se borra físicamente, hash de
 * contraseña incluido, ya que ninguna entidad lo referencia. Si es
 * funcionario, se marca inactivo en lugar de borrarse, porque sus fundamentos
 * de resolución lo referencian y forman parte de la auditoría; si se le
 * vuelve a dar de alta, se reactiva.
 *
 * Un administrador no puede darse de baja a sí mismo, y antes de la baja se
 * verifica, con las filas de administradores bloqueadas, que quien la hace
 * sigue siendo administrador. Así, aunque dos administradores se eliminen
 * entre sí a la vez, siempre queda al menos uno.
 *
 * La baja tiene efecto desde la próxima solicitud del usuario, aunque tenga
 * un token vigente: la autenticación del backoffice y la del frontoffice de
 * funcionarios consultan la base en cada solicitud.
 */
@Stateless
public class BajaUsuarioService {

    private static final Logger LOG = Logger.getLogger(BajaUsuarioService.class.getName());

    @EJB
    private UsuarioDAO usuarios;

    public record UsuarioEliminado(String ci, List<RolUsuario> roles) {
    }

    /**
     * @throws DatosUsuarioInvalidosException si la cédula no es válida
     * @throws UsuarioNoEncontradoException si no es funcionario, administrador ni autoridad
     * @throws BajaNoPermitidaException si es el propio administrador o quien
     *         la hace ya no es administrador
     */
    public UsuarioEliminado eliminar(String ciAdministrador, String ci) {
        String cedula = ValidacionUsuario.cedula(ci);

        if (cedula.equals(ciAdministrador)) {
            throw new BajaNoPermitidaException("No podés eliminar tu propio usuario");
        }
        // Otro administrador pudo haberle quitado el rol mientras tanto
        if (!usuarios.bloquearAdministradores().contains(ciAdministrador)) {
            throw new BajaNoPermitidaException("Tu usuario ya no es administrador");
        }

        RolUsuario rolBackoffice = usuarios.rolBackoffice(cedula);
        Funcionario funcionario = usuarios.buscarFuncionario(cedula);
        boolean esFuncionario = funcionario != null && funcionario.isActivo();

        if (rolBackoffice == null && !esFuncionario) {
            throw new UsuarioNoEncontradoException(
                    "La cédula " + cedula + " no es funcionario, administrador ni autoridad");
        }

        List<RolUsuario> roles = new ArrayList<>();

        if (rolBackoffice != null) {
            usuarios.eliminarUsuarioBackoffice(cedula, rolBackoffice);
            roles.add(rolBackoffice);
        }
        if (esFuncionario) {
            funcionario.setActivo(false);
            roles.add(RolUsuario.FUNCIONARIO);
        }

        LOG.info(() -> LogBackoffice.evento("usuario.baja")
                .usuario(ciAdministrador)
                .campo("ci", cedula)
                .campo("roles", roles)
                .toString());

        return new UsuarioEliminado(cedula, List.copyOf(roles));
    }
}
