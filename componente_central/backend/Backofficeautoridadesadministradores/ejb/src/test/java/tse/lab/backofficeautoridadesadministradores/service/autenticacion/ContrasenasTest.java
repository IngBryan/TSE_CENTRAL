
package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ContrasenasTest {

    @Test
    void verificaContrasenaCorrecta() {
        String hash = Contrasenas.generarHash(
                "ContrasenaSegura2026!");

        assertTrue(Contrasenas.verificar(
                "ContrasenaSegura2026!", hash));
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        String hash = Contrasenas.generarHash(
                "ContrasenaSegura2026!");

        assertFalse(Contrasenas.verificar(
                "OtraContrasena", hash));
    }

    @Test
    void utilizaSalesDiferentes() {
        String hash1 = Contrasenas.generarHash(
                "ContrasenaSegura2026!");

        String hash2 = Contrasenas.generarHash(
                "ContrasenaSegura2026!");

        assertNotEquals(hash1, hash2);

        assertTrue(Contrasenas.verificar(
                "ContrasenaSegura2026!", hash1));

        assertTrue(Contrasenas.verificar(
                "ContrasenaSegura2026!", hash2));
    }

    @Test
    void rechazaHashInvalido() {
        assertFalse(Contrasenas.verificar(
                "ContrasenaSegura2026!", "valor-invalido"));

        assertFalse(Contrasenas.verificar(
                "ContrasenaSegura2026!", null));
    }

    @Test
    void rechazaHashesMalFormados() {
        String hash = Contrasenas.generarHash("ContrasenaSegura2026!");
        String[] partes = hash.split(":");

        // otro algoritmo, iteraciones fuera de rango o no numericas, base64 invalido, largos incorrectos
        for (String invalido : new String[] {
                "md5:" + partes[1] + ":" + partes[2] + ":" + partes[3],
                partes[0] + ":99999:" + partes[2] + ":" + partes[3],
                partes[0] + ":2000001:" + partes[2] + ":" + partes[3],
                partes[0] + ":muchas:" + partes[2] + ":" + partes[3],
                partes[0] + ":" + partes[1] + ":%%%:" + partes[3],
                partes[0] + ":" + partes[1] + ":" + partes[3] + ":" + partes[3],
                partes[0] + ":" + partes[1] + ":" + partes[2] + ":" + partes[2] }) {
            assertFalse(Contrasenas.verificar("ContrasenaSegura2026!", invalido), invalido);
        }
        assertFalse(Contrasenas.verificar(null, hash));
    }

    @Test
    void laContrasenaEsObligatoria() {
        assertThrows(IllegalArgumentException.class, () -> Contrasenas.generarHash(null));
        assertThrows(IllegalArgumentException.class, () -> Contrasenas.generarHash(""));
    }
}
