package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

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
 * Alta de los usuarios internos del MTOP: administradores, autoridades y
 * funcionarios. Choferes y responsables no se crean acá: tienen su propio
 * caso de uso con sus reglas (CU-02 y la gestión de empresas).
 *
 * Administradores y autoridades entran con cédula y contraseña; la
 * contraseña inicial la define el administrador y se guarda hasheada. Los
 * funcionarios entran con gub.uy, así que solo se registran cédula y email.
 */
@Stateless
public class AltaUsuarioService {

    private static final Logger LOG = Logger.getLogger(AltaUsuarioService.class.getName());

    @EJB
    private UsuarioDAO usuarios;

    public record UsuarioCreado(String ci, RolUsuario rol) {
    }

    /**
     * Da de alta un usuario. Valida todos los datos antes de calcular el
     * hash de la contraseña, que es costoso. Un funcionario inactivo (se le
     * había quitado el rol) se reactiva con el email nuevo.
     *
     * @param ciAdministrador quién hace el alta, para el log
     * @param tipo ADMINISTRADOR, AUTORIDAD o FUNCIONARIO
     * @param contrasena obligatoria para administrador y autoridad
     * @param email obligatorio para funcionario
     * @throws DatosUsuarioInvalidosException si algún dato no es válido
     * @throws UsuarioExistenteException si la cédula ya es funcionario,
     *         administrador o autoridad
     */
    public UsuarioCreado crear(
            String ciAdministrador,
            String tipo,
            String ci,
            String contrasena,
            String email) {

        RolUsuario rol = ValidacionUsuario.rolInterno(tipo);
        String cedula = ValidacionUsuario.cedula(ci);

        String correo = null;
        if (rol == RolUsuario.FUNCIONARIO) {
            correo = ValidacionUsuario.email(email);
        } else {
            ValidacionUsuario.contrasena(contrasena);
        }

        Funcionario funcionario = usuarios.buscarFuncionario(cedula);
        sinRolInterno(cedula, rol, funcionario);

        switch (rol) {
            case ADMINISTRADOR -> usuarios.guardar(
                    new Administrador(cedula, Contrasenas.generarHash(contrasena)));
            case AUTORIDAD -> usuarios.guardar(
                    new Autoridad(cedula, Contrasenas.generarHash(contrasena)));
            case FUNCIONARIO -> crearFuncionario(cedula, correo, funcionario);
            default -> throw new IllegalStateException("Tipo no admitido: " + rol);
        }

        LOG.info(() -> LogBackoffice.evento("usuario.alta")
                .usuario(ciAdministrador)
                .campo("rol", rol)
                .campo("ci", cedula)
                .toString());

        return new UsuarioCreado(cedula, rol);
    }

    // existente: un funcionario inactivo, que se reactiva, o null
    private void crearFuncionario(String cedula, String email, Funcionario existente) {
        if (existente == null) {
            usuarios.guardar(new Funcionario(cedula, email));
            return;
        }
        existente.setActivo(true);
        existente.setEmail(email);
    }

    // Una persona tiene un solo rol interno (como en CambioRolService): si ya
    // tiene uno, para darle otro está el cambio de rol
    private void sinRolInterno(String cedula, RolUsuario nuevo, Funcionario funcionario) {
        RolUsuario actual = usuarios.rolBackoffice(cedula);
        if (actual == null && funcionario != null && funcionario.isActivo()) {
            actual = RolUsuario.FUNCIONARIO;
        }
        if (actual == null) {
            return;
        }

        String mensaje = "La cédula " + cedula + " ya es " + actual.name().toLowerCase();
        throw new UsuarioExistenteException(actual == nuevo
                ? mensaje
                : mensaje + "; para darle otro rol usá el cambio de rol");
    }
}
