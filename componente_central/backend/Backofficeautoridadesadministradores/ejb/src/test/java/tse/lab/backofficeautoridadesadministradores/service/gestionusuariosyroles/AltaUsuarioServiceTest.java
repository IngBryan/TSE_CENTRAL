package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService.UsuarioCreado;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.Funcionario;
import tse.lab.model.UsuarioBackoffice;

class AltaUsuarioServiceTest {

    private static final String CONTRASENA = "Clave-segura-2026";

    private AltaUsuarioService servicio;
    private UsuarioDAOFalso usuarios;

    // DAO simulado para probar el servicio sin base de datos.
    static class UsuarioDAOFalso extends UsuarioDAO {

        final List<Object> guardados = new ArrayList<>();

        @Override
        public boolean existeUsuarioBackoffice(String ci) {
            return guardados.stream().anyMatch(u -> u instanceof UsuarioBackoffice b && b.getCi().equals(ci));
        }

        @Override
        public boolean existeFuncionario(String ci) {
            return guardados.stream().anyMatch(u -> u instanceof Funcionario f && f.getCi().equals(ci));
        }

        @Override
        public void guardar(Object usuario) {
            guardados.add(usuario);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        usuarios = new UsuarioDAOFalso();
        servicio = new AltaUsuarioService();

        Field campo = AltaUsuarioService.class.getDeclaredField("usuarios");
        campo.setAccessible(true);
        campo.set(servicio, usuarios);
    }

    private Object unicoGuardado() {
        assertEquals(1, usuarios.guardados.size());
        return usuarios.guardados.get(0);
    }

    @Test
    void creaUnAdministradorConLaContrasenaHasheada() {
        UsuarioCreado creado = servicio.crear("99999999", "ADMINISTRADOR", "1.234.567-8", CONTRASENA, null);

        assertEquals(new UsuarioCreado("12345678", RolUsuario.ADMINISTRADOR), creado);
        Administrador administrador = assertInstanceOf(Administrador.class, unicoGuardado());
        assertEquals("12345678", administrador.getCi());
        assertNotEquals(CONTRASENA, administrador.getContrasena());
        assertTrue(Contrasenas.verificar(CONTRASENA, administrador.getContrasena()));
    }

    @Test
    void creaUnaAutoridad() {
        UsuarioCreado creado = servicio.crear("99999999", "autoridad", "1234567", CONTRASENA, null);

        assertEquals(RolUsuario.AUTORIDAD, creado.rol());
        assertInstanceOf(Autoridad.class, unicoGuardado());
    }

    @Test
    void creaUnFuncionarioConSuEmail() {
        UsuarioCreado creado = servicio.crear("99999999", "FUNCIONARIO", "1234567", null, " ana@mtop.gub.uy ");

        assertEquals(RolUsuario.FUNCIONARIO, creado.rol());
        Funcionario funcionario = assertInstanceOf(Funcionario.class, unicoGuardado());
        assertEquals("ana@mtop.gub.uy", funcionario.getEmail());
    }

    @Test
    void unaAutoridadNoPuedeSerTambienAdministrador() {
        servicio.crear("99999999", "AUTORIDAD", "1234567", CONTRASENA, null);

        assertThrows(UsuarioExistenteException.class,
                () -> servicio.crear("99999999", "ADMINISTRADOR", "1234567", CONTRASENA, null));
        assertEquals(1, usuarios.guardados.size());
    }

    @Test
    void unFuncionarioNoSeRepitePeroPuedeSerAdministrador() {
        servicio.crear("99999999", "FUNCIONARIO", "1234567", null, "ana@mtop.gub.uy");

        assertThrows(UsuarioExistenteException.class,
                () -> servicio.crear("99999999", "FUNCIONARIO", "1234567", null, "otra@mtop.gub.uy"));

        servicio.crear("99999999", "ADMINISTRADOR", "1234567", CONTRASENA, null);
        assertEquals(2, usuarios.guardados.size());
    }

    @ParameterizedTest
    @CsvSource({
        // tipo, ci, contrasena, email
        "CHOFER, 1234567, Clave-segura-2026,",
        "RESPONSABLE, 1234567, Clave-segura-2026,",
        ", 1234567, Clave-segura-2026,",
        "ADMINISTRADOR, 12345, Clave-segura-2026,",
        "ADMINISTRADOR, 123456789, Clave-segura-2026,",
        "ADMINISTRADOR, 12a4567, Clave-segura-2026,",
        "ADMINISTRADOR, , Clave-segura-2026,",
        "ADMINISTRADOR, 1234567, corta,",
        "AUTORIDAD, 1234567, ,",
        "FUNCIONARIO, 1234567, , sin-arroba",
        "FUNCIONARIO, 1234567, ,",
    })
    void rechazaDatosInvalidosSinGuardarNada(String tipo, String ci, String contrasena, String email) {
        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.crear("99999999", tipo, ci, contrasena, email));
        assertTrue(usuarios.guardados.isEmpty());
    }

    @Test
    void rechazaUnaContrasenaDemasiadoLarga() {
        String larga = "x".repeat(AltaUsuarioService.LARGO_MAXIMO_CONTRASENA + 1);

        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.crear("99999999", "ADMINISTRADOR", "1234567", larga, null));
    }

    @Test
    void aceptaUnaContrasenaDelLargoMinimo() {
        String justa = "x".repeat(AltaUsuarioService.LARGO_MINIMO_CONTRASENA);

        servicio.crear("99999999", "ADMINISTRADOR", "1234567", justa, null);

        assertEquals(1, usuarios.guardados.size());
    }
}
