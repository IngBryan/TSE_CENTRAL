package tse.lab.backofficeautoridadesadministradores.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import tse.lab.backofficeautoridadesadministradores.service.autenticacion.CuentaBackoffice;
import tse.lab.model.Administrador;
import tse.lab.model.Autoridad;

// Consulta del rol contra H2.
class UsuarioBackofficeDAOTest {

    private static final String ADMINISTRADOR = "44444444";
    private static final String AUTORIDAD = "55555555";

    private EntityManagerFactory emf;
    private EntityManager em;
    private final UsuarioBackofficeDAO usuarios = new UsuarioBackofficeDAO();

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("backofficeTestPersistenceUnit");
        em = emf.createEntityManager();
        Field campo = UsuarioBackofficeDAO.class.getDeclaredField("em");
        campo.setAccessible(true);
        campo.set(usuarios, em);

        em.getTransaction().begin();
        em.persist(new Administrador(ADMINISTRADOR, "hash"));
        Autoridad autoridad = new Autoridad(AUTORIDAD, "hash");
        autoridad.setDebeCambiarContrasena(true);
        em.persist(autoridad);
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void devuelveElRolSegunElSubtipoYElCambioPendiente() {
        assertEquals(new CuentaBackoffice(ADMINISTRADOR, "ADMINISTRADOR", false), usuarios.buscarCuenta(ADMINISTRADOR));
        assertEquals(new CuentaBackoffice(AUTORIDAD, "AUTORIDAD", true), usuarios.buscarCuenta(AUTORIDAD));
        assertNull(usuarios.buscarCuenta("99999999"));
    }

    @Test
    void actualizaLaContrasenaSiElHashAnteriorCoincideYLiberaElCambioPendiente() {
        em.getTransaction().begin();
        boolean actualizada = usuarios.actualizarContrasena(AUTORIDAD, "hash", "hash-nuevo");
        em.getTransaction().commit();
        em.clear();

        assertTrue(actualizada);
        assertEquals("hash-nuevo", usuarios.buscarPorCi(AUTORIDAD).getContrasena());
        assertEquals("hash", usuarios.buscarPorCi(ADMINISTRADOR).getContrasena());
        assertEquals(new CuentaBackoffice(AUTORIDAD, "AUTORIDAD", false), usuarios.buscarCuenta(AUTORIDAD));
    }

    // Como datos-prueba.sql y las bases migradas: un INSERT sin la columna no queda obligado
    @Test
    void sinIndicarloNoTieneElCambioPendiente() {
        em.getTransaction().begin();
        em.createNativeQuery("INSERT INTO usuarios_backoffice (ci, contrasena) VALUES ('66666666', 'hash')")
                .executeUpdate();
        em.createNativeQuery("INSERT INTO autoridades (ci) VALUES ('66666666')").executeUpdate();
        em.getTransaction().commit();
        em.clear();

        assertFalse(usuarios.buscarCuenta("66666666").debeCambiarContrasena());
    }

    @Test
    void noActualizaSiOtroCambioYaReemplazoElHash() {
        em.getTransaction().begin();
        boolean actualizada = usuarios.actualizarContrasena(ADMINISTRADOR, "otro-hash", "hash-nuevo");
        boolean inexistente = usuarios.actualizarContrasena("99999999", "hash", "hash-nuevo");
        em.getTransaction().commit();
        em.clear();

        assertFalse(actualizada);
        assertFalse(inexistente);
        assertEquals("hash", usuarios.buscarPorCi(ADMINISTRADOR).getContrasena());
    }
}
