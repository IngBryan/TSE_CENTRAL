package tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.BajaUsuarioService.UsuarioEliminado;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolServiceTest.UsuarioDAOFalso;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.Funcionario;

class BajaUsuarioServiceTest {

    private static final String ADMIN_QUE_ELIMINA = "99999999";
    private static final String CI = "1234567";

    private BajaUsuarioService servicio;
    private UsuarioDAOFalso usuarios;

    @BeforeEach
    void setUp() throws Exception {
        usuarios = new UsuarioDAOFalso();
        servicio = new BajaUsuarioService();

        Field campo = BajaUsuarioService.class.getDeclaredField("usuarios");
        campo.setAccessible(true);
        campo.set(servicio, usuarios);

        usuarios.backoffice.put(ADMIN_QUE_ELIMINA, new Administrador(ADMIN_QUE_ELIMINA, "hash"));
    }

    private Funcionario funcionario(boolean activo) {
        Funcionario f = new Funcionario(CI, "ana@mtop.gub.uy");
        f.setActivo(activo);
        usuarios.funcionarios.put(CI, f);
        return f;
    }

    @Test
    void eliminaAUnAdministrador() {
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        UsuarioEliminado eliminado = servicio.eliminar(ADMIN_QUE_ELIMINA, "1.234.567");

        assertEquals(new UsuarioEliminado(CI, List.of(RolUsuario.ADMINISTRADOR)), eliminado);
        assertNull(usuarios.backoffice.get(CI));
    }

    @Test
    void eliminaAUnaAutoridad() {
        usuarios.backoffice.put(CI, new Autoridad(CI, "hash"));

        assertEquals(List.of(RolUsuario.AUTORIDAD), servicio.eliminar(ADMIN_QUE_ELIMINA, CI).roles());
        assertNull(usuarios.backoffice.get(CI));
    }

    @Test
    void unFuncionarioQuedaInactivoSinBorrarse() {
        Funcionario f = funcionario(true);

        assertEquals(List.of(RolUsuario.FUNCIONARIO), servicio.eliminar(ADMIN_QUE_ELIMINA, CI).roles());

        assertFalse(f.isActivo());
        assertNotNull(usuarios.funcionarios.get(CI));
    }

    @Test
    void funcionarioYAdministradorPierdeLosDosRoles() {
        Funcionario f = funcionario(true);
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        UsuarioEliminado eliminado = servicio.eliminar(ADMIN_QUE_ELIMINA, CI);

        assertEquals(List.of(RolUsuario.ADMINISTRADOR, RolUsuario.FUNCIONARIO), eliminado.roles());
        assertNull(usuarios.backoffice.get(CI));
        assertFalse(f.isActivo());
    }

    @Test
    void unAdministradorNoPuedeEliminarse() {
        assertThrows(BajaNoPermitidaException.class,
                () -> servicio.eliminar(ADMIN_QUE_ELIMINA, "9.999.999-9"));
        assertNotNull(usuarios.backoffice.get(ADMIN_QUE_ELIMINA));
    }

    // Otro administrador lo eliminó mientras tanto: si dos se eliminan entre sí
    // a la vez, el segundo no puede dejar el sistema sin administradores
    @Test
    void quienYaNoEsAdministradorNoPuedeEliminar() {
        usuarios.backoffice.remove(ADMIN_QUE_ELIMINA);
        usuarios.backoffice.put(CI, new Administrador(CI, "hash"));

        assertThrows(BajaNoPermitidaException.class, () -> servicio.eliminar(ADMIN_QUE_ELIMINA, CI));

        assertNotNull(usuarios.backoffice.get(CI));
    }

    @Test
    void quienNoEsUsuarioInternoNoSeEncuentra() {
        assertThrows(UsuarioNoEncontradoException.class, () -> servicio.eliminar(ADMIN_QUE_ELIMINA, CI));

        // Un funcionario que ya estaba inactivo tampoco
        Funcionario f = funcionario(false);
        assertThrows(UsuarioNoEncontradoException.class, () -> servicio.eliminar(ADMIN_QUE_ELIMINA, CI));
        assertFalse(f.isActivo());
    }

    @Test
    void rechazaUnaCedulaInvalida() {
        assertThrows(DatosUsuarioInvalidosException.class, () -> servicio.eliminar(ADMIN_QUE_ELIMINA, "12"));
        assertNotNull(usuarios.backoffice.get(ADMIN_QUE_ELIMINA));
    }
}
