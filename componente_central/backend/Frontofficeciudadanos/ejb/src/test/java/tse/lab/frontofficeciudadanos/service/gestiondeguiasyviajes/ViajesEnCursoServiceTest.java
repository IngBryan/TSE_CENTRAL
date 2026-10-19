package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;
import tse.lab.model.EstadoGuia;
import tse.lab.model.Guia;
import tse.lab.model.Vehiculo;

// Viajes en curso de un vehiculo (EJB remoto de la fiscalizacion automatica) contra H2 con el DAO real.
class ViajesEnCursoServiceTest {

    private EntityManagerFactory emf;
    private EntityManager em;
    private ViajesEnCursoService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        GuiaDAO guias = new GuiaDAO();
        set(guias, "em", em);
        servicio = new ViajesEnCursoService();
        set(servicio, "guias", guias);

        Empresa empresa = new Empresa(1L, "Transportes del Este", "Razón social", "Dirección", null);
        Vehiculo camion = new Vehiculo("SBC1234", "Scania", "R 450", 9000, 26000, empresa);
        Vehiculo otro = new Vehiculo("SNO0001", "Volvo", "FH", 9000, 26000, empresa);
        Ciudadano persona = new Ciudadano("56087406", null, null);
        Chofer chofer = new Chofer(persona, empresa, EstadoChofer.HABILITADO);
        em.getTransaction().begin();
        for (Object o : List.of(empresa, camion, otro, persona, chofer)) {
            em.persist(o);
        }
        em.persist(guia("G-ASIGNADA", empresa, camion, chofer, EstadoGuia.ASIGNADA, LocalDate.of(2026, 10, 13)));
        em.persist(guia("G-EN-CURSO", empresa, camion, chofer, EstadoGuia.EN_CURSO, LocalDate.of(2026, 10, 12)));
        em.persist(guia("G-FINALIZADA", empresa, camion, chofer, EstadoGuia.FINALIZADA, LocalDate.of(2026, 10, 11)));
        em.persist(guia("G-CANCELADA", empresa, camion, chofer, EstadoGuia.CANCELADA, LocalDate.of(2026, 10, 11)));
        em.persist(guia("G-OTRO", empresa, otro, chofer, EstadoGuia.EN_CURSO, LocalDate.of(2026, 10, 12)));
        em.persist(new Guia("G-SIN-ASIGNAR", "Forestal", 1000, LocalDate.of(2026, 10, 12), "a", "b", empresa));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    private static Guia guia(String id, Empresa empresa, Vehiculo vehiculo, Chofer chofer, EstadoGuia estado,
            LocalDate fecha) {
        Guia g = new Guia(id, "Forestal", 18000, fecha, "Rivera", "Montevideo", empresa);
        g.asignar(vehiculo, chofer);
        g.cambiarEstado(estado);
        return g;
    }

    private static void set(Object destino, String campo, Object valor) throws ReflectiveOperationException {
        Field field = destino.getClass().getDeclaredField(campo);
        field.setAccessible(true);
        field.set(destino, valor);
    }

    @Test
    void devuelveLasGuiasAsignadasYEnCursoDelVehiculo() {
        List<DatosViaje> viajes = servicio.buscarViajesEnCurso("SBC1234");

        assertEquals(List.of("G-EN-CURSO", "G-ASIGNADA"), viajes.stream().map(DatosViaje::getIdGuia).toList());
        DatosViaje enCurso = viajes.getFirst();
        assertEquals("SBC1234", enCurso.getMatricula());
        assertEquals(1L, enCurso.getNroEmpresa());
        assertEquals("EN_CURSO", enCurso.getEstado());
        assertEquals(LocalDate.of(2026, 10, 12), enCurso.getFecha());
        assertEquals("Rivera", enCurso.getOrigen());
        assertEquals("Montevideo", enCurso.getDestino());
        assertEquals(18000, enCurso.getPesoCargaKg());
    }

    @Test
    void sinViajesEnCursoDevuelveListaVacia() {
        assertEquals(List.of(), servicio.buscarViajesEnCurso("XXX0000"));
        assertEquals(List.of(), servicio.buscarViajesEnCurso(" "));
        assertEquals(List.of(), servicio.buscarViajesEnCurso(null));
    }
}
