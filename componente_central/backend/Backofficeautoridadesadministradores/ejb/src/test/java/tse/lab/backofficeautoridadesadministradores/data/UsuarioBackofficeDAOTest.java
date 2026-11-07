package tse.lab.backofficeautoridadesadministradores.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
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
        em.persist(new Autoridad(AUTORIDAD, "hash"));
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void devuelveElRolSegunElSubtipo() {
        assertEquals("ADMINISTRADOR", usuarios.buscarRol(ADMINISTRADOR));
        assertEquals("AUTORIDAD", usuarios.buscarRol(AUTORIDAD));
        assertNull(usuarios.buscarRol("99999999"));
    }
}
