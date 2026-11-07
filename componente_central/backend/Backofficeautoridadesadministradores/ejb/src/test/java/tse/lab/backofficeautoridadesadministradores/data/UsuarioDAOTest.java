package tse.lab.backofficeautoridadesadministradores.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.RolUsuario;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioExistenteException;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.Funcionario;
import tse.lab.model.Responsable;
import tse.lab.model.UsuarioBackoffice;

// Consultas del listado de usuarios contra H2.
class UsuarioDAOTest {

    private static final String RESPONSABLE = "11111111";
    private static final String CHOFER = "22222222";
    private static final String FUNCIONARIO = "33333333";
    private static final String ADMINISTRADOR = "44444444";
    private static final String AUTORIDAD = "55555555";
    private static final String CIUDADANO_SIN_ROL = "66666666";
    private static final List<String> USUARIOS =
            List.of(RESPONSABLE, CHOFER, FUNCIONARIO, ADMINISTRADOR, AUTORIDAD);

    private EntityManagerFactory emf;
    private EntityManager em;
    private final UsuarioDAO usuarios = new UsuarioDAO();

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        Field campo = UsuarioDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(usuarios, em);

        Ciudadano responsable = new Ciudadano(RESPONSABLE, "ana@example.com", null);
        Ciudadano chofer = new Ciudadano(CHOFER, null, null);
        Empresa norte = new Empresa(1L, "Norte", "Norte S.A.", "Ruta 5", null);
        Empresa sur = new Empresa(2L, "Sur", "Sur S.A.", "Ruta 1", null);

        em.getTransaction().begin();
        em.persist(responsable);
        em.persist(chofer);
        em.persist(new Ciudadano(CIUDADANO_SIN_ROL, null, null));
        em.persist(norte);
        em.persist(sur);
        em.persist(new Responsable(responsable, norte));
        // Chofer de dos empresas y ademas funcionario: sigue siendo un solo usuario
        em.persist(new Chofer(chofer, norte));
        em.persist(new Chofer(chofer, sur));
        em.persist(new Funcionario(CHOFER, "chofer@mtop.gub.uy"));
        em.persist(new Funcionario(FUNCIONARIO, "func@mtop.gub.uy"));
        em.persist(new Administrador(ADMINISTRADOR, "hash"));
        em.persist(new Autoridad(AUTORIDAD, "hash"));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void cuentaCadaCedulaUnaVezYSinLosCiudadanosSinRol() {
        assertEquals(5, usuarios.contarUsuarios(null, null));
    }

    @Test
    void paginaLasCedulasOrdenadas() {
        assertEquals(USUARIOS.subList(0, 2), usuarios.listarCedulas(null, null, 0, 2));
        assertEquals(USUARIOS.subList(2, 4), usuarios.listarCedulas(null, null, 2, 2));
        assertEquals(USUARIOS.subList(4, 5), usuarios.listarCedulas(null, null, 4, 2));
    }

    @Test
    void filtraPorRol() {
        assertEquals(List.of(CHOFER, FUNCIONARIO), usuarios.listarCedulas(RolUsuario.FUNCIONARIO, null, 0, 10));
        assertEquals(List.of(RESPONSABLE), usuarios.listarCedulas(RolUsuario.RESPONSABLE, null, 0, 10));
        assertEquals(List.of(ADMINISTRADOR), usuarios.listarCedulas(RolUsuario.ADMINISTRADOR, null, 0, 10));
        assertEquals(List.of(AUTORIDAD), usuarios.listarCedulas(RolUsuario.AUTORIDAD, null, 0, 10));
        assertEquals(2, usuarios.contarUsuarios(RolUsuario.FUNCIONARIO, null));
    }

    @Test
    void unChoferDeVariasEmpresasCuentaUnaVezAlFiltrarPorRol() {
        assertEquals(1, usuarios.contarUsuarios(RolUsuario.CHOFER, null));
        assertEquals(List.of(CHOFER), usuarios.listarCedulas(RolUsuario.CHOFER, null, 0, 10));
    }

    @Test
    void buscaPorElComienzoDeLaCedula() {
        assertEquals(List.of(CHOFER), usuarios.listarCedulas(null, "222", 0, 10));
        assertEquals(1, usuarios.contarUsuarios(null, "222"));
        assertEquals(0, usuarios.contarUsuarios(null, "666"));
        assertEquals(List.of(CHOFER), usuarios.listarCedulas(RolUsuario.FUNCIONARIO, "2", 0, 10));
        assertEquals(0, usuarios.contarUsuarios(RolUsuario.AUTORIDAD, "4"));
    }

    @Test
    void traeLosDatosSoloDeLasCedulasPedidas() {
        List<Ciudadano> ciudadanos = usuarios.buscarCiudadanos(List.of(RESPONSABLE, FUNCIONARIO));
        List<Funcionario> funcionarios = usuarios.buscarFuncionarios(List.of(RESPONSABLE, FUNCIONARIO));

        assertEquals(List.of(RESPONSABLE), ciudadanos.stream().map(Ciudadano::getCi).toList());
        assertEquals(List.of(FUNCIONARIO), funcionarios.stream().map(Funcionario::getCi).toList());
    }

    @Test
    void filtraLasCedulasDeCadaRol() {
        assertEquals(List.of(RESPONSABLE), usuarios.filtrarResponsables(USUARIOS));
        assertEquals(List.of(ADMINISTRADOR), usuarios.filtrarAdministradores(USUARIOS));
        assertEquals(List.of(AUTORIDAD), usuarios.filtrarAutoridades(USUARIOS));
        assertEquals(List.of(), usuarios.filtrarAutoridades(List.of(ADMINISTRADOR)));
    }

    @Test
    void unChoferDeVariasEmpresasApareceUnaSolaVez() {
        assertEquals(List.of(CHOFER), usuarios.filtrarChoferes(USUARIOS));
    }

    @Test
    void sabeSiLaCedulaYaEsUsuarioDeBackofficeOFuncionario() {
        assertTrue(usuarios.existeUsuarioBackoffice(ADMINISTRADOR));
        assertTrue(usuarios.existeUsuarioBackoffice(AUTORIDAD));
        assertFalse(usuarios.existeUsuarioBackoffice(FUNCIONARIO));
        assertEquals(FUNCIONARIO, usuarios.buscarFuncionario(FUNCIONARIO).getCi());
        assertNull(usuarios.buscarFuncionario(ADMINISTRADOR));
    }

    @Test
    void guardaUnUsuarioNuevoYApareceEnElListado() {
        em.getTransaction().begin();
        usuarios.guardar(new Autoridad(CIUDADANO_SIN_ROL, "hash"));
        usuarios.guardar(new Funcionario("77777777", "nuevo@mtop.gub.uy"));
        em.getTransaction().commit();

        assertTrue(usuarios.existeUsuarioBackoffice(CIUDADANO_SIN_ROL));
        assertTrue(usuarios.buscarFuncionario("77777777").isActivo());
        assertEquals(7, usuarios.contarUsuarios(null, null));
        assertEquals(List.of(AUTORIDAD, CIUDADANO_SIN_ROL),
                usuarios.listarCedulas(RolUsuario.AUTORIDAD, null, 0, 10));
    }

    @Test
    void sabeElRolDeBackofficeDeCadaCedula() {
        assertEquals(RolUsuario.ADMINISTRADOR, usuarios.rolBackoffice(ADMINISTRADOR));
        assertEquals(RolUsuario.AUTORIDAD, usuarios.rolBackoffice(AUTORIDAD));
        assertNull(usuarios.rolBackoffice(FUNCIONARIO));
    }

    @Test
    void cambiaDeAdministradorAAutoridadConservandoLaContrasena() {
        em.getTransaction().begin();
        usuarios.cambiarRolBackoffice(ADMINISTRADOR, RolUsuario.ADMINISTRADOR, RolUsuario.AUTORIDAD);
        em.getTransaction().commit();
        em.clear();

        UsuarioBackoffice usuario = em.find(UsuarioBackoffice.class, ADMINISTRADOR);
        assertInstanceOf(Autoridad.class, usuario);
        assertEquals("hash", usuario.getContrasena());
        assertEquals(RolUsuario.AUTORIDAD, usuarios.rolBackoffice(ADMINISTRADOR));
        assertEquals(List.of(), usuarios.listarCedulas(RolUsuario.ADMINISTRADOR, null, 0, 10));
    }

    // Simula dos altas simultáneas: la base rechaza la segunda por clave duplicada
    @Test
    void unAltaDuplicadaEsUsuarioExistente() {
        em.getTransaction().begin();
        em.clear();

        assertThrows(UsuarioExistenteException.class,
                () -> usuarios.guardar(new Funcionario(FUNCIONARIO, "otra@mtop.gub.uy")));
        em.getTransaction().rollback();
        em.clear();

        em.getTransaction().begin();
        assertThrows(UsuarioExistenteException.class,
                () -> usuarios.guardar(new Administrador(ADMINISTRADOR, "hash")));
        em.getTransaction().rollback();
    }

    @Test
    void bloqueaYDevuelveSoloLosAdministradores() {
        em.getTransaction().begin();
        assertEquals(List.of(ADMINISTRADOR), usuarios.bloquearAdministradores());
        em.getTransaction().commit();
    }

    @Test
    void eliminaAlUsuarioDeBackofficeConSuContrasena() {
        em.getTransaction().begin();
        usuarios.eliminarUsuarioBackoffice(AUTORIDAD, RolUsuario.AUTORIDAD);
        em.getTransaction().commit();
        em.clear();

        assertNull(em.find(UsuarioBackoffice.class, AUTORIDAD));
        assertFalse(usuarios.existeUsuarioBackoffice(AUTORIDAD));
        assertEquals(4, usuarios.contarUsuarios(null, null));
    }

    @Test
    void unFuncionarioInactivoNoApareceComoFuncionario() {
        em.getTransaction().begin();
        usuarios.buscarFuncionario(FUNCIONARIO).setActivo(false);
        em.getTransaction().commit();

        // CHOFER sigue siendo funcionario; FUNCIONARIO no tiene otro rol y sale del listado
        assertEquals(List.of(CHOFER), usuarios.listarCedulas(RolUsuario.FUNCIONARIO, null, 0, 10));
        assertEquals(4, usuarios.contarUsuarios(null, null));
        assertEquals(List.of(), usuarios.buscarFuncionarios(List.of(FUNCIONARIO)));
        assertFalse(usuarios.buscarFuncionario(FUNCIONARIO).isActivo());
    }
}
