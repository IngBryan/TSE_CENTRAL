package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.backofficeautoridadesadministradores.service.LogBackoffice;
import tse.lab.model.Funcionario;

/**
 * Baja de un funcionario, administrador o autoridad: se le quitan todos
 * esos roles.
 *
 * <ul>
 * <li>Administrador o autoridad: se borra, con su contraseña (nada lo
 * referencia).</li>
 * <li>Funcionario: no se borra, queda inactivo. Sus fundamentos de
 * resolución lo referencian y son parte de la auditoría; si se lo vuelve a
 * dar de alta se reactiva.</li>
 * <li>Los roles de chofer y responsable no se tocan: tienen su propio ciclo
 * de vida.</li>
 * <li>Un administrador no puede eliminarse a sí mismo.</li>
 * </ul>
 *
 * Rige en el siguiente pedido: la autenticación del backoffice y la del
 * frontoffice de funcionarios verifican el usuario en la base.
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
     * @throws BajaNoPermitidaException si es el propio administrador
     */
    public UsuarioEliminado eliminar(String ciAdministrador, String ci) {
        String cedula = ValidacionUsuario.cedula(ci);

        if (cedula.equals(ciAdministrador)) {
            throw new BajaNoPermitidaException("No podés eliminar tu propio usuario");
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
