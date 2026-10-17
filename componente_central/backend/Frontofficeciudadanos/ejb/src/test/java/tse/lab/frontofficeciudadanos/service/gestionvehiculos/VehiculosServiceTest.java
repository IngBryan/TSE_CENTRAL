package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.model.Empresa;
import tse.lab.model.Vehiculo;

// Busqueda de vehiculos (EJB remoto de la fiscalizacion automatica) contra H2 con el DAO real.
class VehiculosServiceTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private VehiculosService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        VehiculoDAO vehiculos = new VehiculoDAO();
        set(vehiculos, "em", em);
        servicio = new VehiculosService();
        set(servicio, "vehiculos", vehiculos);

        Empresa empresa = new Empresa(1L, "Transportes del Este", "Razón social", "Dirección", null);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(new Vehiculo("SBC1234", "Scania", "R 450", 9000, 26000, empresa));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    private static void set(Object destino, String campo, Object valor) throws ReflectiveOperationException {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }

    @Test
    void devuelveElVehiculoConSuEmpresa() {
        assertEquals(new DatosVehiculo("SBC1234", 1L), servicio.buscarVehiculo("SBC1234"));
    }

    @Test
    void vehiculoQueNoExisteDevuelveNull() {
        assertNull(servicio.buscarVehiculo("XXX0000"));
        assertNull(servicio.buscarVehiculo(" "));
        assertNull(servicio.buscarVehiculo(null));
    }
}
