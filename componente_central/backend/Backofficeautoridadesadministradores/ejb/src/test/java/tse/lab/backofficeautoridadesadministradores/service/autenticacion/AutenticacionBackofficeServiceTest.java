
package tse.lab.backofficeautoridadesadministradores.service.autenticacion;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioBackofficeDAO;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.UsuarioBackoffice;

class AutenticacionBackofficeServiceTest {

    private static final String CLAVE = "ClaveDePrueba2026!";
    private static String hash;

    private AutenticacionBackofficeService servicio;
    private UsuarioBackofficeDAOFalso dao;

    static class UsuarioBackofficeDAOFalso
            extends UsuarioBackofficeDAO {

        private final Map<String, UsuarioBackoffice> datos =
                new HashMap<>();

        @Override
        public UsuarioBackoffice buscarPorCi(String ci) {
            return datos.get(ci);
        }
    }

    @BeforeAll
    static void prepararHash() {
        hash = Contrasenas.generarHash(CLAVE);
    }

    @BeforeEach
    void preparar() throws Exception {
        dao = new UsuarioBackofficeDAOFalso();
        servicio = new AutenticacionBackofficeService();

        Field campo = AutenticacionBackofficeService.class
                .getDeclaredField("usuarios");

        campo.setAccessible(true);
        campo.set(servicio, dao);
    }

    @Test
    void autenticaAdministrador() {
        dao.datos.put("12345678",
                new Administrador("12345678", hash));

        var resultado = servicio.autenticar(
                "12345678", CLAVE);

        assertTrue(resultado.isPresent());
        assertEquals("12345678", resultado.get().ci());
        assertEquals("ADMINISTRADOR", resultado.get().rol());
    }

    @Test
    void autenticaAutoridad() {
        dao.datos.put("87654321",
                new Autoridad("87654321", hash));

        var resultado = servicio.autenticar(
                "87654321", CLAVE);

        assertTrue(resultado.isPresent());
        assertEquals("AUTORIDAD", resultado.get().rol());
    }

    @Test
    void rechazaContrasenaIncorrecta() {
        dao.datos.put("12345678",
                new Administrador("12345678", hash));

        assertTrue(servicio.autenticar(
                "12345678", "Incorrecta").isEmpty());
    }

    @Test
    void rechazaUsuarioInexistente() {
        assertTrue(servicio.autenticar(
                "99999999", CLAVE).isEmpty());
    }

    @Test
    void rechazaCredencialesVacias() {
        assertTrue(servicio.autenticar(
                null, CLAVE).isEmpty());

        assertTrue(servicio.autenticar(
                "12345678", null).isEmpty());

        assertTrue(servicio.autenticar(
                "  ", CLAVE).isEmpty());
    }
}
