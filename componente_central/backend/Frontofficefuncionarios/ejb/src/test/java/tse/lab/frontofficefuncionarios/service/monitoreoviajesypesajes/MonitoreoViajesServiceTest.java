package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.CiudadanoDAO;
import tse.lab.frontofficefuncionarios.data.EventoPesajesDAO;
import tse.lab.frontofficefuncionarios.data.EventoViajeDAO;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.data.GuiaDAO;
import tse.lab.frontofficefuncionarios.data.ModificacionGuiaDAO;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.ChoferVista;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.EventoVista;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.PaginaViajes;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.PesajeVista;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.ViajeDetalle;
import tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes.MonitoreoViajesService.ViajeResumen;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoGuia;
import tse.lab.model.EventoPesajes;
import tse.lab.model.EventoViaje;
import tse.lab.model.Funcionario;
import tse.lab.model.Guia;
import tse.lab.model.ModificacionGuia;
import tse.lab.model.NodoBalanza;
import tse.lab.model.ResultadoEventoViaje;
import tse.lab.model.TipoEventoViaje;
import tse.lab.model.TipoModificacionGuia;
import tse.lab.model.Vehiculo;

// CU Monitorear viajes y pesajes contra H2 con los DAOs reales.
// GV-1: FINALIZADA (INICIO 10:00, FIN 14:00), GV-2: EN_CURSO sin INICIO registrado, GV-3: ASIGNADA (no
// es un viaje todavia: no se muestra).
class MonitoreoViajesServiceTest {

    private static final String FUNCIONARIO = "45678901";
    private static final String NO_FUNCIONARIO = "54074380";
    private static final String CHOFER = "56087406";
    private static final String RESPONSABLE = "11111111";
    private static final byte[] FOTO = { (byte) 0xFF, (byte) 0xD8, 1, 2, 3 };

    private EntityManagerFactory emf;
    private EntityManager em;
    private MonitoreoViajesService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionFuncionario autorizacion = new AutorizacionFuncionario();
        set(autorizacion, "funcionarios", conEm(new FuncionarioDAO()));
        servicio = new MonitoreoViajesService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "guias", conEm(new GuiaDAO()));
        set(servicio, "eventos", conEm(new EventoViajeDAO()));
        set(servicio, "modificaciones", conEm(new ModificacionGuiaDAO()));
        set(servicio, "pesajes", conEm(new EventoPesajesDAO()));
        set(servicio, "ciudadanos", conEm(new CiudadanoDAO()));

        Empresa este = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        Empresa norte = new Empresa(2L, "Fletes del Norte", "FDN S.R.L.", "Rivera", null);
        Ciudadano persona = new Ciudadano(CHOFER, "chofer@example.com", "sub-1");
        persona.setPrimerNombre("Juan");
        persona.setPrimerApellido("Gómez");
        Chofer chofer = new Chofer(persona, este);
        Chofer choferNorte = new Chofer(persona, norte);
        // Peso maximo: 9000 + 26000 = 35000
        Vehiculo sba = new Vehiculo("SBA1234", "Scania", "R 450", 9000, 26000, este);
        Vehiculo sab = new Vehiculo("SAB5678", "Volvo", "FH", 8000, 20000, norte);

        Guia gv1 = new Guia("GV-1", "Agropecuario", 18000, LocalDate.of(2026, 10, 10), "Montevideo", "Paysandú",
                este);
        gv1.asignar(sba, chofer);
        gv1.cambiarEstado(EstadoGuia.FINALIZADA);
        Guia gv2 = new Guia("GV-2", "Forestal", 24000, LocalDate.of(2026, 10, 12), "Rivera", "Montevideo", norte);
        gv2.asignar(sab, choferNorte);
        gv2.cambiarEstado(EstadoGuia.EN_CURSO);
        Guia gv3 = new Guia("GV-3", "Agropecuario", 10000, LocalDate.of(2026, 10, 11), "Pando", "Minas", este);
        gv3.asignar(sba, chofer);

        NodoBalanza balanza = new NodoBalanza("Peaje Pando", "Ruta 8 km 32", 1L);

        em.getTransaction().begin();
        em.persist(new Funcionario(FUNCIONARIO, "funcionario@mtop.gub.uy"));
        em.persist(este);
        em.persist(norte);
        em.persist(persona);
        em.persist(chofer);
        em.persist(choferNorte);
        em.persist(sba);
        em.persist(sab);
        em.persist(gv1);
        em.persist(gv2);
        em.persist(gv3);
        em.persist(balanza);
        // Pesajes de SBA1234: antes, durante (uno con exceso) y despues de GV-1
        em.persist(new EventoPesajes(1L, "SBA1234", t("09:00"), 30000, balanza));
        em.persist(new EventoPesajes(2L, "SBA1234", t("12:00"), 38500, balanza));
        em.persist(new EventoPesajes(3L, "SBA1234", t("13:00"), 30000, balanza));
        em.persist(new EventoPesajes(4L, "SBA1234", t("15:00"), 30000, balanza));
        // SAB5678: uno anterior a la asignacion de GV-2 y uno posterior
        em.persist(new EventoPesajes(5L, "SAB5678", Instant.parse("2026-01-01T12:00:00Z"), 20000, balanza));
        em.persist(new EventoPesajes(6L, "SAB5678", Instant.now().plus(1, ChronoUnit.HOURS), 25000, balanza));

        em.persist(evento("e-inicio", gv1, TipoEventoViaje.INICIO, "10:00", null, ResultadoEventoViaje.APLICADO));
        em.persist(evento("e-inicio-2", gv1, TipoEventoViaje.INICIO, "10:05", null, ResultadoEventoViaje.SIN_EFECTO));
        em.persist(evento("e-incidente", gv1, TipoEventoViaje.INCIDENTE, "11:00", FOTO, ResultadoEventoViaje.APLICADO));
        em.persist(evento("e-fin", gv1, TipoEventoViaje.FIN, "14:00", null, ResultadoEventoViaje.APLICADO));
        em.persist(evento("e-carga", gv1, TipoEventoViaje.CARGA_PARCIAL, "14:30", null,
                ResultadoEventoViaje.CONFLICTO));
        em.persist(new ModificacionGuia(gv1, TipoModificacionGuia.EDICION, RESPONSABLE, "destino: Salto -> Paysandú",
                null, 0L, 1L));
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void quienNoEsFuncionarioNoAccedeANingunaOperacion() {
        for (String ci : new String[] { NO_FUNCIONARIO, null, " " }) {
            assertThrows(NoAutorizadoException.class, () -> servicio.listar(ci, null, null, null, null, null, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.detalle(ci, "GV-1", "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.fotoEvento(ci, "GV-1", "e-incidente", "t"));
        }
    }

    // ===== Listado =====

    @Test
    void listaSoloLosViajesEnCursoOFinalizadosDelMasNuevoAlMasViejo() throws Exception {
        PaginaViajes p = servicio.listar(FUNCIONARIO, null, null, null, null, null, "t");

        // GV-3 (ASIGNADA, sin empezar) no aparece
        ChoferVista juan = new ChoferVista(CHOFER, "Juan", "Gómez");
        assertEquals(List.of(
                new ViajeResumen("GV-2", "2026-10-12", "Rivera", "Montevideo", "EN_CURSO", 2L, "Fletes del Norte",
                        "SAB5678", juan),
                new ViajeResumen("GV-1", "2026-10-10", "Montevideo", "Paysandú", "FINALIZADA", 1L,
                        "Transportes del Este", "SBA1234", juan)),
                p.viajes());
        assertEquals(2, p.total());
        assertEquals(1, p.totalPaginas());
    }

    @Test
    void filtraPorEstadoMatriculaYEmpresa() throws Exception {
        assertEquals(List.of("GV-2"), ids(servicio.listar(FUNCIONARIO, "en_curso", null, null, null, null, "t")));
        // La matricula se normaliza como al darla de alta; GV-3 tambien es de SBA1234 pero no empezo
        assertEquals(List.of("GV-1"), ids(servicio.listar(FUNCIONARIO, null, "sba-1234", null, null, null, "t")));
        assertEquals(List.of("GV-2"), ids(servicio.listar(FUNCIONARIO, null, null, "2", null, null, "t")));
        assertEquals(List.of("GV-1"),
                ids(servicio.listar(FUNCIONARIO, "FINALIZADA", "SBA1234", "1", null, null, "t")));
        assertEquals(List.of(), ids(servicio.listar(FUNCIONARIO, "EN_CURSO", null, "1", null, null, "t")));
    }

    @Test
    void paginaElListado() throws Exception {
        PaginaViajes segunda = servicio.listar(FUNCIONARIO, null, null, null, "2", "1", "t");
        assertEquals(List.of("GV-1"), ids(segunda));
        assertEquals(2, segunda.total());
        assertEquals(2, segunda.totalPaginas());

        PaginaViajes fuera = servicio.listar(FUNCIONARIO, null, null, null, "5", "1", "t");
        assertTrue(fuera.viajes().isEmpty());
        assertEquals(2, fuera.total());
    }

    @Test
    void rechazaFiltrosYPaginacionInvalidos() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.listar(FUNCIONARIO, "VOLANDO", null, "abc", "0", "101", "t"));
        assertEquals(List.of("estado", "nroEmpresa", "pagina", "tamanio"), List.copyOf(e.getErrores().keySet()));
    }

    @Test
    void filtrarPorUnEstadoDeGuiaQueNoEsViajeEsInvalido() {
        for (String estado : new String[] { "SIN_ASIGNAR", "ASIGNADA", "CANCELADA" }) {
            DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                    () -> servicio.listar(FUNCIONARIO, estado, null, null, null, null, "t"));
            assertEquals(List.of("estado"), List.copyOf(e.getErrores().keySet()));
        }
    }

    // ===== Detalle =====

    @Test
    void detalleConLineaDeTiempoModificacionesYPesajesDuranteElViaje() throws Exception {
        ViajeDetalle d = servicio.detalle(FUNCIONARIO, "GV-1", "t");

        assertEquals("FINALIZADA", d.estado());
        assertEquals("Transportes del Este", d.empresa());
        assertEquals(35000, d.vehiculo().pesoMaximoKg());
        assertEquals(new ChoferVista(CHOFER, "Juan", "Gómez"), d.chofer());
        assertEquals(t("10:00").toString(), d.inicio());
        assertEquals(t("14:00").toString(), d.fin());

        assertEquals(List.of("e-inicio", "e-inicio-2", "e-incidente", "e-fin", "e-carga"),
                d.eventos().stream().map(EventoVista::idEvento).toList());
        assertEquals(List.of("APLICADO", "SIN_EFECTO", "APLICADO", "APLICADO", "CONFLICTO"),
                d.eventos().stream().map(EventoVista::resultado).toList());
        EventoVista incidente = d.eventos().get(2);
        assertTrue(incidente.conFoto());
        assertFalse(d.eventos().get(0).conFoto());
        assertEquals(new ChoferVista(CHOFER, "Juan", "Gómez"), incidente.chofer());

        assertEquals(1, d.modificaciones().size());
        assertEquals("EDICION", d.modificaciones().get(0).tipo());
        assertEquals(RESPONSABLE, d.modificaciones().get(0).ciResponsable());

        // Solo los pesajes entre el INICIO y el FIN, con el exceso sobre el peso maximo
        assertEquals(List.of(new PesajeVista(t("12:00").toString(), "Peaje Pando", 38500, 3500),
                new PesajeVista(t("13:00").toString(), "Peaje Pando", 30000, 0)), d.pesajes());
    }

    @Test
    void unViajeEnCursoSinInicioRegistradoTomaLosPesajesDesdeLaAsignacion() throws Exception {
        ViajeDetalle d = servicio.detalle(FUNCIONARIO, "GV-2", "t");

        assertNull(d.inicio());
        assertNull(d.fin());
        assertTrue(d.eventos().isEmpty());
        assertEquals(List.of(25000L), d.pesajes().stream().map(PesajeVista::pesoKg).toList());
    }

    @Test
    void unaGuiaQueNoEmpezoNoSePuedeConsultarComoViaje() {
        assertEquals("VIAJE_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.detalle(FUNCIONARIO, "GV-3", "t")).getCodigo());
        assertEquals("VIAJE_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.fotoEvento(FUNCIONARIO, "GV-3", "e-incidente", "t")).getCodigo());
    }

    @Test
    void unViajeInexistenteDa404() {
        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.detalle(FUNCIONARIO, "GV-99", "t"));
        assertEquals("VIAJE_NO_ENCONTRADO", e.getCodigo());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.detalle(FUNCIONARIO, null, "t"));
    }

    // ===== Foto de un evento =====

    @Test
    void devuelveLaFotoDeUnEventoDelViaje() throws Exception {
        assertArrayEquals(FOTO, servicio.fotoEvento(FUNCIONARIO, "GV-1", "e-incidente", "t"));
    }

    @Test
    void sinFotoOEventoDeOtroViajeDa404() {
        assertEquals("SIN_FOTO", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.fotoEvento(FUNCIONARIO, "GV-1", "e-inicio", "t")).getCodigo());
        assertEquals("EVENTO_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.fotoEvento(FUNCIONARIO, "GV-2", "e-incidente", "t")).getCodigo());
        assertEquals("EVENTO_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.fotoEvento(FUNCIONARIO, "GV-1", "no-existe", "t")).getCodigo());
    }

    private static Instant t(String hora) {
        return Instant.parse("2026-10-10T" + hora + ":00Z");
    }

    private static EventoViaje evento(String id, Guia guia, TipoEventoViaje tipo, String hora, byte[] foto,
            ResultadoEventoViaje resultado) {
        EventoViaje e = new EventoViaje(id, guia, CHOFER, tipo, t(hora), null, -34.9, -56.1, foto);
        e.registrarResultado(resultado, null, null, guia.getEstado(), 0L);
        return e;
    }

    private static List<String> ids(PaginaViajes p) {
        return p.viajes().stream().map(ViajeResumen::id).toList();
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
