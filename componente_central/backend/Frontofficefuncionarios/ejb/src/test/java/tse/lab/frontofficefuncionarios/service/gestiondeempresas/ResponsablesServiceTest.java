package tse.lab.frontofficefuncionarios.service.gestiondeempresas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.api.gestiondeempresas.DatosResponsable;
import tse.lab.frontofficefuncionarios.data.ResponsableDAO;
import tse.lab.frontofficefuncionarios.model.Responsable;

// Vista remota de los responsables contra H2 con el DAO real.
class ResponsablesServiceTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private ResponsablesService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        ResponsableDAO dao = new ResponsableDAO();
        set(dao, "em", em);
        servicio = new ResponsablesService();
        set(servicio, "responsables", dao);

        em.getTransaction().begin();
        em.persist(new Responsable("54074380", null, 1L));
        em.persist(new Responsable("11111111", "tenia@prueba.uy", 2L));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void buscarPorCiDevuelveLosDatosDelResponsable() {
        assertEquals(new DatosResponsable("11111111", "tenia@prueba.uy", 2L), servicio.buscarPorCi("11111111"));
    }

    @Test
    void buscarPorCiDevuelveNullSiNoEsResponsable() {
        assertNull(servicio.buscarPorCi("99999999"));
        assertNull(servicio.buscarPorCi(null));
    }

    @Test
    void completarEmailSoloSiNoTenia() {
        em.getTransaction().begin();
        servicio.completarEmail("54074380", "nuevo@prueba.uy");
        servicio.completarEmail("11111111", "otro@prueba.uy");
        servicio.completarEmail("99999999", "x@prueba.uy");
        servicio.completarEmail(null, "x@prueba.uy");
        em.getTransaction().commit();
        em.clear();

        assertEquals("nuevo@prueba.uy", em.find(Responsable.class, "54074380").getEmail());
        assertEquals("tenia@prueba.uy", em.find(Responsable.class, "11111111").getEmail());
    }

    @Test
    void completarEmailNoBorraConNull() {
        em.getTransaction().begin();
        servicio.completarEmail("11111111", null);
        em.getTransaction().commit();
        em.clear();

        assertEquals("tenia@prueba.uy", em.find(Responsable.class, "11111111").getEmail());
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
