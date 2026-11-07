package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.Locale;
import java.util.logging.Logger;
import java.util.regex.Pattern;

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

    static final int LARGO_MINIMO_CONTRASENA = 12;
    static final int LARGO_MAXIMO_CONTRASENA = 128;
    static final int LARGO_MAXIMO_EMAIL = 254;

    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    @EJB
    private UsuarioDAO usuarios;

    public record UsuarioCreado(String ci, RolUsuario rol) {
    }

    /**
     * Da de alta un usuario. Valida todos los datos antes de calcular el
     * hash de la contraseña, que es costoso.
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

        RolUsuario rol = tipo(tipo);
        String cedula = cedula(ci);

        Object usuario = switch (rol) {
            case ADMINISTRADOR -> {
                validarContrasena(contrasena);
                noExisteEnBackoffice(cedula);
                yield new Administrador(cedula, Contrasenas.generarHash(contrasena));
            }
            case AUTORIDAD -> {
                validarContrasena(contrasena);
                noExisteEnBackoffice(cedula);
                yield new Autoridad(cedula, Contrasenas.generarHash(contrasena));
            }
            case FUNCIONARIO -> {
                String correo = email(email);
                if (usuarios.existeFuncionario(cedula)) {
                    throw new UsuarioExistenteException("La cédula " + cedula + " ya es funcionario");
                }
                yield new Funcionario(cedula, correo);
            }
            default -> throw new IllegalStateException("Tipo no admitido: " + rol);
        };

        usuarios.guardar(usuario);

        LOG.info(() -> LogBackoffice.evento("usuario.alta")
                .usuario(ciAdministrador)
                .campo("rol", rol)
                .campo("ci", cedula)
                .toString());

        return new UsuarioCreado(cedula, rol);
    }

    private static RolUsuario tipo(String valor) {
        String tipo = valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
        return switch (tipo) {
            case "ADMINISTRADOR" -> RolUsuario.ADMINISTRADOR;
            case "AUTORIDAD" -> RolUsuario.AUTORIDAD;
            case "FUNCIONARIO" -> RolUsuario.FUNCIONARIO;
            default -> throw new DatosUsuarioInvalidosException(
                    "El tipo de usuario tiene que ser ADMINISTRADOR, AUTORIDAD o FUNCIONARIO");
        };
    }

    // Quita puntos, guiones y espacios: 5.407.438-0 es 54074380
    private static String cedula(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("[.\\-\\s]", "");
        if (!CEDULA.matcher(digitos).matches()) {
            throw new DatosUsuarioInvalidosException("La cédula tiene que tener de 6 a 8 dígitos");
        }
        return digitos;
    }

    // Sin strip: los espacios son parte de la contraseña
    private static void validarContrasena(String valor) {
        int largo = valor == null ? 0 : valor.codePointCount(0, valor.length());
        if (largo < LARGO_MINIMO_CONTRASENA || largo > LARGO_MAXIMO_CONTRASENA) {
            throw new DatosUsuarioInvalidosException("La contraseña tiene que tener entre "
                    + LARGO_MINIMO_CONTRASENA + " y " + LARGO_MAXIMO_CONTRASENA + " caracteres");
        }
    }

    private static String email(String valor) {
        String email = valor == null ? "" : valor.strip();
        if (email.length() > LARGO_MAXIMO_EMAIL || !EMAIL.matcher(email).matches()) {
            throw new DatosUsuarioInvalidosException("El email no es válido");
        }
        return email;
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
