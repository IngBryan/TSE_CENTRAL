package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import tse.lab.backofficeautoridadesadministradores.data.UsuarioDAO;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.Contrasenas;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolService.RolCambiado;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.Funcionario;
import tse.lab.model.UsuarioBackoffice;

class CambioRolServiceTest {

    private static final String ADMIN_QUE_CAMBIA = "99999999";
    private static final String CI = "1234567";
    private static final String CONTRASENA = "Clave-segura-2026";

    private CambioRolService servicio;
    private UsuarioDAOFalso usuarios;

    // Simula las tablas: usuarios del backoffice (con su subtipo) y funcionarios.
    static class UsuarioDAOFalso extends UsuarioDAO {

        final Map<String, UsuarioBackoffice> backoffice = new HashMap<>();
        final Map<String, Funcionario> funcionarios = new HashMap<>();

        @Override
        public RolUsuario rolBackoffice(String ci) {
            UsuarioBackoffice u = backoffice.get(ci);
            if (u instanceof Administrador) {
                return RolUsuario.ADMINISTRADOR;
            }
            return u instanceof Autoridad ? RolUsuario.AUTORIDAD : null;
        }

        @Override
        public Funcionario buscarFuncionario(String ci) {
            return funcionarios.get(ci);
        }

        @Override
        public void guardar(Object usuario) {
            if (usuario instanceof Funcionario f) {
                funcionarios.put(f.getCi(), f);
            } else {
                UsuarioBackoffice u = (UsuarioBackoffice) usuario;
                backoffice.put(u.getCi(), u);
            }
        }

        @Override
        public void cambiarRolBackoffice(String ci, RolUsuario anterior, RolUsuario nuevo) {
            String hash = backoffice.get(ci).getContrasena();
            backoffice.put(ci, nuevo == RolUsuario.ADMINISTRADOR
                    ? new Administrador(ci, hash)
                    : new Autoridad(ci, hash));
        }

        @Override
        public void eliminarUsuarioBackoffice(String ci, RolUsuario rol) {
            backoffice.remove(ci);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        usuarios = new UsuarioDAOFalso();
        servicio = new CambioRolService();

        Field campo = CambioRolService.class.getDeclaredField("usuarios");
        campo.setAccessible(true);
        campo.set(servicio, usuarios);
    }

    private RolCambiado cambiar(String rol, String contrasena, String email) {
        return servicio.cambiar(ADMIN_QUE_CAMBIA, CI, rol, contrasena, email);
    }

    private Funcionario funcionario(boolean activo) {
        Funcionario f = new Funcionario(CI, "ana@mtop.gub.uy");
        f.setActivo(activo);
        usuarios.funcionarios.put(CI, f);
        return f;
    }

    @Test
    void deAdministradorAAutoridadConservaLaContrasena() {
        usuarios.backoffice.put(CI, new Administrador(CI, "hash-original"));

        assertEquals(new RolCambiado(CI, RolUsuario.AUTORIDAD), cambiar("AUTORIDAD", null, null));

        Autoridad autoridad = assertInstanceOf(Autoridad.class, usuarios.backoffice.get(CI));
        assertEquals("hash-original", autoridad.getContrasena());
    }

    @Test
    void deAutoridadAAdministrador() {
        usuarios.backoffice.put(CI, new Autoridad(CI, "hash-original"));

        cambiar("administrador", null, null);

        assertInstanceOf(Administrador.class, usuarios.backoffice.get(CI));
    }

    @Test
    void deFuncionarioAAdministradorLoDejaInactivoYPideContrasena() {
        Funcionario f = funcionario(true);

        cambiar("ADMINISTRADOR", CONTRASENA, null);

        assertFalse(f.isActivo());
        Administrador admin = assertInstanceOf(Administrador.class, usuarios.backoffice.get(CI));
        assertTrue(Contrasenas.verificar(CONTRASENA, admin.getContrasena()));
    }

    @Test
    void deFuncionarioAAutoridadSinContrasenaNoCambiaNada() {
        Funcionario f = funcionario(true);

        assertThrows(DatosUsuarioInvalidosException.class, () -> cambiar("AUTORIDAD", null, null));

        assertTrue(f.isActivo());
        assertTrue(usuarios.backoffice.isEmpty());
    }

    @Test
    void deAdministradorAFuncionarioPideEmailYQuitaElUsuarioDeBackoffice() {
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        cambiar("FUNCIONARIO", null, "ana@mtop.gub.uy");

        assertNull(usuarios.backoffice.get(CI));
        assertTrue(usuarios.funcionarios.get(CI).isActivo());
        assertEquals("ana@mtop.gub.uy", usuarios.funcionarios.get(CI).getEmail());
    }

    @Test
    void deAutoridadAFuncionarioSinEmailNoCambiaNada() {
        usuarios.backoffice.put(CI, new Autoridad(CI, "hash"));

        assertThrows(DatosUsuarioInvalidosException.class, () -> cambiar("FUNCIONARIO", null, "sin-arroba"));

        assertInstanceOf(Autoridad.class, usuarios.backoffice.get(CI));
        assertTrue(usuarios.funcionarios.isEmpty());
    }

    @Test
    void unFuncionarioInactivoQueVuelveSeReactiva() {
        Funcionario f = funcionario(false);
        usuarios.backoffice.put(CI, new Autoridad(CI, "hash"));

        cambiar("FUNCIONARIO", null, "nuevo@mtop.gub.uy");

        assertTrue(f.isActivo());
        assertEquals("nuevo@mtop.gub.uy", f.getEmail());
        assertNull(usuarios.backoffice.get(CI));
    }

    @Test
    void funcionarioYAdministradorQuedaSoloComoAdministrador() {
        Funcionario f = funcionario(true);
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        cambiar("ADMINISTRADOR", null, null);

        assertFalse(f.isActivo());
        assertInstanceOf(Administrador.class, usuarios.backoffice.get(CI));
    }

    @Test
    void funcionarioYAdministradorQuedaSoloComoFuncionarioSinPedirEmail() {
        Funcionario f = funcionario(true);
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        cambiar("FUNCIONARIO", null, null);

        assertTrue(f.isActivo());
        assertEquals("ana@mtop.gub.uy", f.getEmail());
        assertNull(usuarios.backoffice.get(CI));
    }

    @Test
    void siYaTieneSoloEseRolNoSePermite() {
        usuarios.backoffice.put(CI, new Autoridad(CI, "hash"));

        assertThrows(CambioRolNoPermitidoException.class, () -> cambiar("AUTORIDAD", null, null));
    }

    @Test
    void unAdministradorNoPuedeCambiarSuPropioRol() {
        usuarios.backoffice.put(ADMIN_QUE_CAMBIA, new Administrador(ADMIN_QUE_CAMBIA, "hash"));

        assertThrows(CambioRolNoPermitidoException.class,
                () -> servicio.cambiar(ADMIN_QUE_CAMBIA, "9.999.999-9", "AUTORIDAD", null, null));
        assertInstanceOf(Administrador.class, usuarios.backoffice.get(ADMIN_QUE_CAMBIA));
    }

    @Test
    void quienNoEsFuncionarioAdministradorNiAutoridadNoSeEncuentra() {
        assertThrows(UsuarioNoEncontradoException.class, () -> cambiar("AUTORIDAD", CONTRASENA, null));
        // Un funcionario inactivo tampoco cuenta
        funcionario(false);
        assertThrows(UsuarioNoEncontradoException.class, () -> cambiar("AUTORIDAD", CONTRASENA, null));
    }

    @ParameterizedTest
    @CsvSource({ "CHOFER, 1234567", "RESPONSABLE, 1234567", ", 1234567", "AUTORIDAD, 12", "AUTORIDAD, abc" })
    void rechazaRolesYCedulasInvalidos(String rol, String ci) {
        assertThrows(DatosUsuarioInvalidosException.class,
                () -> servicio.cambiar(ADMIN_QUE_CAMBIA, ci, rol, CONTRASENA, "a@b.uy"));
    }
}
