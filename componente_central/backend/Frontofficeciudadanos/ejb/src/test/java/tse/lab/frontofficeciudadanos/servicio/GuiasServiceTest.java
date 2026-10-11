package tse.lab.frontofficeciudadanos.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoGuia;
import tse.lab.frontofficeciudadanos.model.EstadoValidacion;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.Responsable;
import tse.lab.frontofficeciudadanos.model.Vehiculo;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.GuiaVista;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.servicio.GuiasService.OpcionesAsignacion;

// Gestion de Viajes de Carga y CU-07 contra H2 con los DAOs reales.
class GuiasServiceTest {

    private static final String RESPONSABLE = "54074380";
    private static final String MANANA = LocalDate.now(ZoneId.of("America/Montevideo")).plusDays(1).toString();

    private EntityManagerFactory emf;
    private EntityManager em;
    private GuiasService servicio;
    private Long choferHabilitado;
    private Long choferPendiente;
    private Long choferOtraEmpresa;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        PerfilCiudadanoDAO perfiles = conEm(new PerfilCiudadanoDAO());
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", conEm(new ResponsableDAO()));
        set(autorizacion, "perfiles", perfiles);
        servicio = new GuiasService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "guias", conEm(new GuiaDAO()));
        set(servicio, "vehiculos", conEm(new VehiculoDAO()));
        set(servicio, "choferes", conEm(new ChoferDAO()));
        set(servicio, "perfiles", perfiles);

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        empresa.setHabilitada(true);
        Empresa otra = new Empresa(2L, "Otra", "Otra S.A.", "Montevideo", null);
        otra.setHabilitada(true);
        PerfilCiudadano responsable = validado(RESPONSABLE, "Emiliano", "Tomas");
        Chofer habilitado = new Chofer("56087406", null, empresa, EstadoChofer.HABILITADO);
        Chofer pendiente = new Chofer("22222222", null, empresa, EstadoChofer.PENDIENTE_VALIDACION);
        Chofer deOtra = new Chofer("77777777", null, otra, EstadoChofer.HABILITADO);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(otra);
        em.persist(new Responsable(RESPONSABLE, null, empresa));
        em.persist(responsable);
        em.persist(validado("56087406", "Bryan", "Salamone"));
        em.persist(new Vehiculo("SBA 1234", "Scania", "R 450", 9000, 26000, empresa));
        em.persist(new Vehiculo("OTR 0001", "Ford", "Cargo", 5000, 8000, otra));
        em.persist(habilitado);
        em.persist(pendiente);
        em.persist(deOtra);
        em.getTransaction().commit();
        choferHabilitado = habilitado.getId();
        choferPendiente = pendiente.getId();
        choferOtraEmpresa = deOtra.getId();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void crearGuiaQuedaSinAsignarYAparaceEnElListado() throws Exception {
        GuiaVista g = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia(" Agropecuario ", 18000L, MANANA, "Montevideo", "Paysandú"), "t"));
        assertTrue(g.id().startsWith("GV-"));
        assertEquals("Agropecuario", g.rubroCliente());
        assertEquals("SIN_ASIGNAR", g.estado());
        assertNull(g.vehiculo());
        assertEquals(List.of(g), servicio.listar(RESPONSABLE, "t"));
    }

    @Test
    void validacionesAlCrear() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("", 0L, "2020-01-01", "Salto", " salto "), "t"));
        assertEquals(4, e.getErrores().size(), e.getErrores().toString());
        assertTrue(e.getErrores().containsKey("rubroCliente"));
        assertTrue(e.getErrores().get("pesoCargaKg").contains("mayor que 0"));
        assertTrue(e.getErrores().get("fecha").contains("anterior a hoy"));
        assertTrue(e.getErrores().get("destino").contains("distinto"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("x", 1L, "10/10/2026", "a", "b"), "t")).getErrores().containsKey("fecha"));
    }

    @Test
    void opcionesListanVehiculosYSoloChoferesHabilitadosDeLaEmpresa() throws Exception {
        GuiaVista g = nuevaGuia();
        OpcionesAsignacion o = servicio.opcionesAsignacion(RESPONSABLE, g.id(), "t");
        assertEquals(List.of("SBA 1234"), o.vehiculos().stream().map(GuiasService.VehiculoResumen::matricula).toList());
        assertEquals(1, o.choferes().size());
        assertEquals(choferHabilitado, o.choferes().get(0).id());
        assertEquals("Bryan", o.choferes().get(0).primerNombre());
    }

    @Test
    void asignarRegistraYLaGuiaNoSePuedeVolverAAsignar() throws Exception {
        GuiaVista g = nuevaGuia();
        GuiaVista asignada = enTx(() -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
        assertEquals("ASIGNADA", asignada.estado());
        assertEquals("SBA 1234", asignada.vehiculo().matricula());
        assertEquals("56087406", asignada.chofer().cedula());
        em.clear();
        assertEquals(EstadoGuia.ASIGNADA, em.find(Guia.class, g.id()).getEstado());

        ConflictoException e = assertThrows(ConflictoException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
        assertEquals("GUIA_YA_ASIGNADA", e.getCodigo());
        assertThrows(ConflictoException.class, () -> servicio.opcionesAsignacion(RESPONSABLE, g.id(), "t"));
    }

    @Test
    void noSeAsignaVehiculoOChoferDeOtraEmpresaNiChoferPendiente() throws Exception {
        GuiaVista g = nuevaGuia();
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "OTR 0001", choferOtraEmpresa, "t"));
        assertEquals(2, e.getErrores().size());
        e = assertThrows(DatosInvalidosException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferPendiente, "t"));
        assertTrue(e.getErrores().get("chofer").contains("pendiente"));
        assertEquals("SIN_ASIGNAR", servicio.listar(RESPONSABLE, "t").get(0).estado());
    }

    @Test
    void guiaInexistenteOSinPermiso() throws Exception {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.opcionesAsignacion(RESPONSABLE, "GV-NOEXISTE", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.listar("56087406", "t"));
        assertThrows(NoAutorizadoException.class,
                () -> servicio.crear(null, new NuevaGuia("x", 1L, MANANA, "a", "b"), "t"));
        // la empresa tiene que estar habilitada
        enTx(() -> {
            em.find(Empresa.class, 1L).setHabilitada(false);
            return null;
        });
        assertThrows(NoAutorizadoException.class, () -> servicio.listar(RESPONSABLE, "t"));
    }

    // ===== helpers =====

    private GuiaVista nuevaGuia() throws Exception {
        return enTx(() -> servicio.crear(RESPONSABLE, new NuevaGuia("Forestal", 20000L, MANANA, "Rivera", "Montevideo"), "t"));
    }

    private static PerfilCiudadano validado(String ci, String nombre, String apellido) {
        PerfilCiudadano p = new PerfilCiudadano(ci, null, null);
        p.setEstado(EstadoValidacion.VALIDADO);
        p.setPrimerNombre(nombre);
        p.setPrimerApellido(apellido);
        return p;
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    private <T> T enTx(Accion<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T r = accion.ejecutar();
            em.getTransaction().commit();
            return r;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        }
    }

    private <T> T conEm(T dao) throws Exception {
        set(dao, "em", em);
        return dao;
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
