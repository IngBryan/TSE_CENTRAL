package tse.lab.frontofficeciudadanos.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;
import tse.lab.model.Responsable;
import tse.lab.model.Vehiculo;

class DAOIntegrationTest {

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManagerFactory = Persistence.createEntityManagerFactory(
                "frontofficeciudadanosTestPersistenceUnit");
        entityManager = entityManagerFactory.createEntityManager();
    }

    @AfterEach
    void tearDown() {
        if (entityManager != null) {
            entityManager.close();
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
        }
    }

    @Test
    void empresaDaoPersisteBuscaYListaEmpresas() {
        EmpresaDAO dao = new EmpresaDAO();
        injectEntityManager(dao);
        Empresa empresa = new Empresa(10L, "Empresa de prueba", "Razón social", "Dirección", null);

        beginTransaction();
        dao.guardar(empresa);
        commitTransaction();

        beginTransaction();
        Empresa encontrada = dao.buscarPorId(10L);
        List<Empresa> empresas = dao.listarTodas();
        commitTransaction();

        assertNotNull(encontrada);
        assertSame(empresa.getNroEmpresa(), encontrada.getNroEmpresa());
        assertEquals(1, empresas.size());
        assertSame(encontrada, empresas.get(0));
    }

    @Test
    void responsableDaoPersisteBuscaYActualiza() {
        ResponsableDAO dao = new ResponsableDAO();
        injectEntityManager(dao);
        Empresa empresa = new Empresa(11L, "Empresa responsable", "Razón social", "Dirección", null);
        Ciudadano persona = new Ciudadano("CI-3", "responsable@example.com", null);
        Responsable responsable = new Responsable(persona, empresa);

        beginTransaction();
        entityManager.persist(empresa);
        entityManager.persist(persona);
        dao.guardar(responsable);
        commitTransaction();

        beginTransaction();
        Responsable encontrada = dao.buscarPorCi("CI-3");
        Responsable actualizada = dao.actualizar(encontrada);
        commitTransaction();

        assertNotNull(actualizada);
        assertEquals("responsable@example.com", actualizada.getCiudadano().getEmail());
        assertSame(empresa, actualizada.getEmpresa());
    }

    @Test
    void choferDaoPersisteBuscaEliminaYBuscaPorEmpresa() {
        ChoferDAO dao = new ChoferDAO();
        injectEntityManager(dao);
        Empresa empresa = new Empresa(12L, "Empresa choferes", "Razón social", "Dirección", null);
        Ciudadano persona = new Ciudadano("CI-4", "chofer@example.com", null);
        Chofer chofer = new Chofer(persona, empresa);

        beginTransaction();
        entityManager.persist(empresa);
        entityManager.persist(persona);
        dao.guardar(chofer);
        commitTransaction();

        beginTransaction();
        Chofer encontrado = dao.buscarPorCi("CI-4").get(0);
        List<Chofer> resultados = dao.buscarPorEmpresa(empresa);
        dao.eliminar(encontrado);
        commitTransaction();

        assertNotNull(encontrado);
        assertEquals(1, resultados.size());
        assertEquals("CI-4", resultados.get(0).getCi());
        assertTrue(dao.buscarPorCi("CI-4").isEmpty());
    }

    @Test
    void vehiculoDaoPersisteBuscaEliminaYBuscaPorEmpresa() {
        VehiculoDAO dao = new VehiculoDAO();
        injectEntityManager(dao);
        Empresa empresa = new Empresa(13L, "Empresa vehículos", "Razón social", "Dirección", null);
        Vehiculo vehiculo = new Vehiculo("ABC-456", "Ford", "EcoSport", 1200L, 500L, empresa);

        beginTransaction();
        entityManager.persist(empresa);
        dao.guardar(vehiculo);
        commitTransaction();

        beginTransaction();
        Vehiculo encontrado = dao.buscarPorMatricula("ABC-456");
        List<Vehiculo> resultados = dao.buscarPorEmpresa(empresa);
        dao.eliminar(encontrado);
        commitTransaction();

        assertNotNull(encontrado);
        assertEquals(1, resultados.size());
        assertEquals("ABC-456", resultados.get(0).getMatricula());
        assertNull(dao.buscarPorMatricula("ABC-456"));
    }

    @Test
    void guiaDaoPersisteBuscaActualizaYFiltraLasGuias() {
        GuiaDAO dao = new GuiaDAO();
        injectEntityManager(dao);
        Empresa empresa = new Empresa(14L, "Empresa guías", "Razón social", "Dirección", null);
        Guia guia = new Guia("GUIA-2", "Transporte", 100L,
                LocalDate.of(2026, 10, 7), "La Plata", "Buenos Aires", empresa);

        beginTransaction();
        entityManager.persist(empresa);
        dao.guardar(guia);
        commitTransaction();

        beginTransaction();
        Guia encontrada = dao.buscarPorId("GUIA-2");
        encontrada.setRubroCliente("Logística");
        Guia actualizada = dao.actualizar(encontrada);
        List<Guia> porEmpresa = dao.buscarPorEmpresa(empresa);
        List<Guia> sinVehiculo = dao.buscarSinVehiculoAsignado();
        commitTransaction();

        assertNotNull(actualizada);
        assertEquals("Logística", actualizada.getRubroCliente());
        assertEquals(1, porEmpresa.size());
        assertEquals(1, sinVehiculo.size());
        assertSame(encontrada, porEmpresa.get(0));
    }

    private void beginTransaction() {
        entityManager.getTransaction().begin();
    }

    private void commitTransaction() {
        entityManager.getTransaction().commit();
    }

    private void injectEntityManager(Object dao) {
        try {
            Field field = dao.getClass().getDeclaredField("em");
            field.setAccessible(true);
            field.set(dao, entityManager);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("No se pudo inyectar EntityManager", exception);
        }
    }
}
