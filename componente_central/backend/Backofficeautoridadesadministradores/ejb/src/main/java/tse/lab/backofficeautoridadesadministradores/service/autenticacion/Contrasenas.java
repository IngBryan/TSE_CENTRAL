
package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Genera y verifica hashes de contraseñas mediante
 * PBKDF2 con HMAC-SHA256.
 *
 * Cada hash utiliza una sal aleatoria y almacena
 * el algoritmo, las iteraciones, la sal y el resultado.
 */
public final class Contrasenas {

    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final String IDENTIFICADOR = "pbkdf2-sha256";

    private static final int ITERACIONES = 600_000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_HASH = 256;

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private Contrasenas() {
    }

    /**
     * Genera un hash con sal aleatoria para almacenar en la base.
     *
     * @param contrasena contraseña original
     * @return representación codificada del hash
     */
    public static String generarHash(String contrasena) {
        if (contrasena == null || contrasena.isEmpty()) {
            throw new IllegalArgumentException(
                    "La contraseña es obligatoria");
        }

        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);

        char[] caracteres = contrasena.toCharArray();

        try {
            byte[] hash = derivar(
                    caracteres, sal, ITERACIONES);

            try {
                return IDENTIFICADOR + ":"
                        + ITERACIONES + ":"
                        + codificar(sal) + ":"
                        + codificar(hash);
            } finally {
                Arrays.fill(hash, (byte) 0);
            }
        } finally {
            Arrays.fill(caracteres, '\0');
        }
    }

    /**
     * Verifica una contraseña contra el hash almacenado.
     *
     * @param contrasena contraseña proporcionada
     * @param hashAlmacenado valor recuperado de PostgreSQL
     * @return true si la contraseña coincide
     */
    public static boolean verificar(
            String contrasena,
            String hashAlmacenado) {

        if (contrasena == null || hashAlmacenado == null) {
            return false;
        }

        String[] partes = hashAlmacenado.split(":", -1);

        if (partes.length != 4
                || !IDENTIFICADOR.equals(partes[0])) {
            return false;
        }

        try {
            int iteraciones = Integer.parseInt(partes[1]);

            if (iteraciones < 100_000
                    || iteraciones > 2_000_000) {
                return false;
            }

            byte[] sal = Base64.getUrlDecoder()
                    .decode(partes[2]);

            byte[] esperado = Base64.getUrlDecoder()
                    .decode(partes[3]);

            if (sal.length != BYTES_SAL
                    || esperado.length != BITS_HASH / 8) {
                return false;
            }

            char[] caracteres = contrasena.toCharArray();

            try {
                byte[] obtenido = derivar(
                        caracteres, sal, iteraciones);

                try {
                    return MessageDigest.isEqual(
                            esperado, obtenido);
                } finally {
                    Arrays.fill(obtenido, (byte) 0);
                }
            } finally {
                Arrays.fill(caracteres, '\0');
            }

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] derivar(
            char[] contrasena,
            byte[] sal,
            int iteraciones) {

        PBEKeySpec especificacion = new PBEKeySpec(
                contrasena, sal, iteraciones, BITS_HASH);

        try {
            SecretKeyFactory fabrica =
                    SecretKeyFactory.getInstance(ALGORITMO);

            return fabrica.generateSecret(
                    especificacion).getEncoded();

        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(
                    "No se pudo procesar la contraseña", e);

        } finally {
            especificacion.clearPassword();
        }
    }

    private static String codificar(byte[] datos) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(datos);
    }
}
