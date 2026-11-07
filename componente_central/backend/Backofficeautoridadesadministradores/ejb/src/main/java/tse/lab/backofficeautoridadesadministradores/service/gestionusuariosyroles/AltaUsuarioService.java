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
     * @throws UsuarioExistenteException si la cédula ya tiene ese usuario
     */
    public UsuarioCreado crear(
            String ciAdministrador,
            String tipo,
            String ci,
            String contrasena,
            String email) {

        RolUsuario rol = ValidacionUsuario.rolInterno(tipo);
        String cedula = ValidacionUsuario.cedula(ci);

        switch (rol) {
            case ADMINISTRADOR -> {
                ValidacionUsuario.contrasena(contrasena);
                noExisteEnBackoffice(cedula);
                usuarios.guardar(new Administrador(cedula, Contrasenas.generarHash(contrasena)));
            }
            case AUTORIDAD -> {
                ValidacionUsuario.contrasena(contrasena);
                noExisteEnBackoffice(cedula);
                usuarios.guardar(new Autoridad(cedula, Contrasenas.generarHash(contrasena)));
            }
            case FUNCIONARIO -> crearFuncionario(cedula, ValidacionUsuario.email(email));
            default -> throw new IllegalStateException("Tipo no admitido: " + rol);
        }

        LOG.info(() -> LogBackoffice.evento("usuario.alta")
                .usuario(ciAdministrador)
                .campo("rol", rol)
                .campo("ci", cedula)
                .toString());

        return new UsuarioCreado(cedula, rol);
    }

    private void crearFuncionario(String cedula, String email) {
        Funcionario existente = usuarios.buscarFuncionario(cedula);

        if (existente == null) {
            usuarios.guardar(new Funcionario(cedula, email));
            return;
        }
        if (existente.isActivo()) {
            throw new UsuarioExistenteException("La cédula " + cedula + " ya es funcionario");
        }
        existente.setActivo(true);
        existente.setEmail(email);
    }

    // Administradores y autoridades comparten la tabla usuarios_backoffice (la cédula es la clave):
    // una persona no puede ser las dos cosas
    private void noExisteEnBackoffice(String cedula) {
        if (usuarios.existeUsuarioBackoffice(cedula)) {
            throw new UsuarioExistenteException(
                    "La cédula " + cedula + " ya es administrador o autoridad");
        }
    }
}
