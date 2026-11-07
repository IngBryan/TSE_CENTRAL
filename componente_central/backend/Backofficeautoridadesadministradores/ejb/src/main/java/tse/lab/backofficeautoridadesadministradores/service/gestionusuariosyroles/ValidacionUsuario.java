package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validaciones de los datos de los usuarios internos del MTOP, comunes al
 * alta y al cambio de rol. Cada una lanza DatosUsuarioInvalidosException.
 */
final class ValidacionUsuario {

    static final int LARGO_MINIMO_CONTRASENA = 12;
    static final int LARGO_MAXIMO_CONTRASENA = 128;
    static final int LARGO_MAXIMO_EMAIL = 254;

    private static final Pattern CEDULA = Pattern.compile("\\d{6,8}");

    private ValidacionUsuario() {
    }

    /**
     * @return ADMINISTRADOR, AUTORIDAD o FUNCIONARIO: los roles que se
     *         gestionan desde el backoffice
     */
    static RolUsuario rolInterno(String valor) {
        String rol = valor == null ? "" : valor.strip().toUpperCase(Locale.ROOT);
        return switch (rol) {
            case "ADMINISTRADOR" -> RolUsuario.ADMINISTRADOR;
            case "AUTORIDAD" -> RolUsuario.AUTORIDAD;
            case "FUNCIONARIO" -> RolUsuario.FUNCIONARIO;
            default -> throw new DatosUsuarioInvalidosException(
                    "El rol tiene que ser ADMINISTRADOR, AUTORIDAD o FUNCIONARIO");
        };
    }

    // Quita puntos, guiones y espacios: 5.407.438-0 es 54074380
    static String cedula(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("[.\\-\\s]", "");
        if (!CEDULA.matcher(digitos).matches()) {
            throw new DatosUsuarioInvalidosException("La cédula tiene que tener de 6 a 8 dígitos");
        }
        return digitos;
    }

    // Sin strip: los espacios son parte de la contraseña
    static void contrasena(String valor) {
        int largo = valor == null ? 0 : valor.codePointCount(0, valor.length());
        if (largo < LARGO_MINIMO_CONTRASENA || largo > LARGO_MAXIMO_CONTRASENA) {
            throw new DatosUsuarioInvalidosException("La contraseña tiene que tener entre "
                    + LARGO_MINIMO_CONTRASENA + " y " + LARGO_MAXIMO_CONTRASENA + " caracteres");
        }
    }

    static String email(String valor) {
        String email = valor == null ? "" : valor.strip();
        if (email.length() > LARGO_MAXIMO_EMAIL || !esEmail(email)) {
            throw new DatosUsuarioInvalidosException("El email no es válido");
        }
        return email;
    }

    // Algo@dominio.algo, sin espacios y con una sola arroba. Se revisa a mano y
    // no con una regex: la equivalente hace backtracking (tiempo super-lineal).
    private static boolean esEmail(String email) {
        int arroba = email.indexOf('@');
        if (arroba <= 0 || arroba != email.lastIndexOf('@')
                || email.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        // Un punto en el dominio con algo antes y algo después
        String dominio = email.substring(arroba + 1);
        int punto = dominio.indexOf('.', 1);
        return punto > 0 && punto < dominio.length() - 1;
    }
}
