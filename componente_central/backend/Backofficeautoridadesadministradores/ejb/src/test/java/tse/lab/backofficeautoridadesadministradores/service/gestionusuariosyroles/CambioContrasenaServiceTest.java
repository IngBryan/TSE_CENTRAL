package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioBackofficeDAO;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.UsuarioBackoffice;

class CambioContrasenaServiceTest {

    private static final String ADMINISTRADOR = "12345678";
    private static final String AUTORIDAD = "87654321";
    private static final String ACTUAL = "Clave-actual-2026";
    private static final String NUEVA = "Clave-nueva-2026!";

    private static String hashActual;

    private CambioContrasenaService servicio;
    private UsuarioBackofficeDAOFalso usuarios;

    // Guarda los hashes en memoria; el update es condicional como en la base
    static class UsuarioBackofficeDAOFalso extends UsuarioBackofficeDAO {

        final Map<String, UsuarioBackoffice> datos = new HashMap<>();
        final Map<String, String> hashes = new HashMap<>();
        int actualizaciones;
        boolean otroCambioGano;

        @Override
        public UsuarioBackoffice buscarPorCi(String ci) {
            return datos.get(ci);
        }

        @Override
        public boolean actualizarContrasena(String ci, String hashAnterior, String hashNuevo) {
            actualizaciones++;
            if (otroCambioGano || !hashAnterior.equals(hashes.get(ci))) {
                return false;
            }
            hashes.put(ci, hashNuevo);
            return true;
        }

        void agregar(UsuarioBackoffice usuario) {
            datos.put(usuario.getCi(), usuario);
            hashes.put(usuario.getCi(), usuario.getContrasena());
        }
    }

    @BeforeAll
    static void prepararHash() {
        hashActual = Contrasenas.generarHash(ACTUAL);
    }

    @BeforeEach
    void preparar() throws Exception {
        usuarios = new UsuarioBackofficeDAOFalso();
        usuarios.agregar(new Administrador(ADMINISTRADOR, hashActual));
        usuarios.agregar(new Autoridad(AUTORIDAD, hashActual));

        servicio = new CambioContrasenaService();
        Field campo = CambioContrasenaService.class.getDeclaredField("usuarios");
        campo.setAccessible(true);
        campo.set(servicio, usuarios);
    }

    @Test
    void cambiaLaContrasenaDeUnAdministrador() {
        servicio.cambiar(ADMINISTRADOR, ACTUAL, NUEVA);

        String guardado = usuarios.hashes.get(ADMINISTRADOR);
        assertNotEquals(hashActual, guardado);
        assertTrue(Contrasenas.verificar(NUEVA, guardado));
        assertFalse(Contrasenas.verificar(ACTUAL, guardado));
    }

    @Test
    void cambiaLaContrasenaDeUnaAutoridad() {
        servicio.cambiar(AUTORIDAD, ACTUAL, NUEVA);

        assertTrue(Contrasenas.verificar(NUEVA, usuarios.hashes.get(AUTORIDAD)));
        assertSame(hashActual, usuarios.hashes.get(ADMINISTRADOR));
    }

    @Test
    void rechazaUnaContrasenaActualIncorrecta() {
        assertThrows(ContrasenaActualIncorrectaException.class,
                () -> servicio.cambiar(ADMINISTRADOR, "Otra-clave-2026", NUEVA));

        assertEquals(0, usuarios.actualizaciones);
        assertSame(hashActual, usuarios.hashes.get(ADMINISTRADOR));
    }

    @Test
    void rechazaSinContrasenaActual() {
        assertThrows(ContrasenaActualIncorrectaException.class,
                () -> servicio.cambiar(ADMINISTRADOR, null, NUEVA));
        assertThrows(ContrasenaActualIncorrectaException.class,
                () -> servicio.cambiar(ADMINISTRADOR, "", NUEVA));

        assertEquals(0, usuarios.actualizaciones);
    }

    @Test
    void rechazaUnaContrasenaNuevaCorta() {
        var error = assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.cambiar(ADMINISTRADOR, ACTUAL, "corta"));

        assertTrue(error.getMessage().contains("12"));
        assertEquals(0, usuarios.actualizaciones);
    }

    @Test
    void rechazaUnaContrasenaNuevaLarga() {
        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.cambiar(ADMINISTRADOR, ACTUAL, "x".repeat(129)));
    }

    @Test
    void rechazaSinContrasenaNueva() {
        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.cambiar(ADMINISTRADOR, ACTUAL, null));
    }

    @Test
    void laNuevaTieneQueSerDistintaDeLaActual() {
        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.cambiar(ADMINISTRADOR, ACTUAL, ACTUAL));

        assertEquals(0, usuarios.actualizaciones);
    }

    @Test
    void rechazaUnaCedulaQueNoEsUsuarioDelBackoffice() {
        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio.cambiar("11111111", ACTUAL, NUEVA));
        assertThrows(UsuarioNoEncontradoException.class,
                () -> servicio.cambiar(null, ACTUAL, NUEVA));
    }

    @Test
    void siOtroCambioGanoLaCarreraNoLaPisa() {
        usuarios.otroCambioGano = true;

        assertThrows(ContrasenaActualIncorrectaException.class,
                () -> servicio.cambiar(ADMINISTRADOR, ACTUAL, NUEVA));

        assertEquals(1, usuarios.actualizaciones);
        assertSame(hashActual, usuarios.hashes.get(ADMINISTRADOR));
    }
}
