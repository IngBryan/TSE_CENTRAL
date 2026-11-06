package tse.lab.frontofficeciudadanos.service.gestionvehiculos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.data.ITVDAO;
import tse.lab.frontofficeciudadanos.data.PermisoCirculacionDAO;
import tse.lab.model.ITV;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.EvidenciaHabilitacion.EstadoDocumento;
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
        var permisos = new PermisoCirculacionDAO();
        set(permisos, "em", em);
        set(servicio, "permisos", permisos);
        var itvs = new ITVDAO();
        set(itvs, "em", em);
        set(servicio, "itvs", itvs);

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
        assertEquals(new DatosVehiculo("SBC1234", 1L, 9000, 26000), servicio.buscarVehiculo("SBC1234"));
    }

    @Test
    void consultaTemporalDevuelveAusenciasYSeleccionaItvDeLaFechaDelEvento() {
        Instant fecha = Instant.parse("2026-11-06T12:00:00Z");
        var sinRegistro = servicio.buscarVehiculo("SBC1234", fecha).getHabilitacion();
        assertEquals(EstadoDocumento.SIN_REGISTRO, sinRegistro.getEstadoPermiso());
        assertEquals(EstadoDocumento.SIN_REGISTRO, sinRegistro.getEstadoItv());
        var vehiculo = em.find(Vehiculo.class, "SBC1234");
        var dia = LocalDate.of(2026, 11, 6);
        em.getTransaction().begin();
        em.persist(new PermisoCirculacion("PNC-1", dia.minusYears(1), dia, vehiculo));
        em.persist(new ITV(dia.minusYears(1), dia, vehiculo));
        em.persist(new ITV(dia.plusDays(1), dia.plusYears(1), vehiculo));
        em.getTransaction().commit();
        var prueba = servicio.buscarVehiculo("SBC1234", fecha).getHabilitacion();
        assertEquals(fecha, prueba.getFechaHora());
        assertEquals(EstadoDocumento.VIGENTE, prueba.getEstadoPermiso());
        assertEquals(EstadoDocumento.VIGENTE, prueba.getEstadoItv());
        assertEquals(dia, prueba.getVencimientoItv());
        assertNull(servicio.buscarVehiculo("XXX0000", fecha));
    }

    @Test
    void vehiculoQueNoExisteDevuelveNull() {
        assertNull(servicio.buscarVehiculo("XXX0000"));
        assertNull(servicio.buscarVehiculo(" "));
        assertNull(servicio.buscarVehiculo(null));
    }
}
