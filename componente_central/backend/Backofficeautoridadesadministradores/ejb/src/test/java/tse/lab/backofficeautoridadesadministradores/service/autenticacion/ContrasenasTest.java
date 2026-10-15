
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
}
