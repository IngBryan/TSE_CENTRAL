package tse.lab.frontofficeciudadanos.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoGuia;
import tse.lab.frontofficeciudadanos.model.EventoViaje;
import tse.lab.frontofficeciudadanos.model.Guia;
import tse.lab.frontofficeciudadanos.model.ResultadoEventoViaje;
import tse.lab.frontofficeciudadanos.model.TipoEventoViaje;
import tse.lab.frontofficeciudadanos.model.Vehiculo;
import tse.lab.frontofficeciudadanos.service.ViajesChoferService.NuevoEvento;
import tse.lab.frontofficeciudadanos.service.ViajesChoferService.ResultadoEvento;
import tse.lab.frontofficeciudadanos.service.ViajesChoferService.ViajeVista;

// API de la app movil (viajes del chofer y eventos del viaje) contra H2 con los DAOs reales.
class ViajesChoferServiceTest {

    private static final String CHOFER = "56087406";
    private static final String HORA = "2026-10-11T21:30:00-03:00";
    // Cabecera JPEG minima: alcanza para la validacion de formato
    private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };

    private EntityManagerFactory emf;
    private EntityManager em;
    private ViajesChoferService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        servicio = new ViajesChoferService();
        set(servicio, "choferes", conEm(new ChoferDAO()));
        set(servicio, "guias", conEm(new GuiaDAO()));
        set(servicio, "eventos", conEm(new EventoViajeDAO()));

        Empresa este = empresa(1L, "Transportes del Este");
        Empresa norte = empresa(2L, "Fletes del Norte");
        Vehiculo camion = new Vehiculo("SBA 1234", "Scania", "R 450", 9000, 26000, este);
        Vehiculo otro = new Vehiculo("SNO 0001", "Volvo", "FH", 9000, 26000, norte);
        Chofer enEste = new Chofer(CHOFER, null, este, EstadoChofer.HABILITADO);
        Chofer enNorte = new Chofer(CHOFER, null, norte, EstadoChofer.HABILITADO);
        Chofer otroChofer = new Chofer("33333333", null, este, EstadoChofer.HABILITADO);
        Chofer pendiente = new Chofer("22222222", null, este, EstadoChofer.PENDIENTE_VALIDACION);
        Chofer sinViajes = new Chofer("44444444", null, este, EstadoChofer.HABILITADO);
        em.getTransaction().begin();
        for (Object o : List.of(este, norte, camion, otro, enEste, enNorte, otroChofer, pendiente, sinViajes)) {
            em.persist(o);
        }
        em.persist(guia("GV-1", este, camion, enEste, LocalDate.of(2026, 10, 12)));
        em.persist(guia("GV-2", norte, otro, enNorte, LocalDate.of(2026, 10, 11)));
        em.persist(guia("GV-OTRO", este, camion, otroChofer, LocalDate.of(2026, 10, 11)));
        em.persist(new Guia("GV-LIBRE", "Forestal", 1000, LocalDate.of(2026, 10, 11), "a", "b", este));
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void viajesDelChoferEnTodasSusEmpresas() throws Exception {
        List<ViajeVista> viajes = servicio.viajes(CHOFER, "t");
        assertEquals(List.of("GV-2", "GV-1"), viajes.stream().map(ViajeVista::id).toList()); // por fecha
        ViajeVista v = viajes.get(1);
        assertEquals(1L, v.nroEmpresa());
        assertEquals("Transportes del Este", v.empresa());
        assertEquals("SBA 1234", v.matricula());
        assertEquals("Granos a granel", v.tipoCarga());
        assertEquals("ASIGNADA", v.estado());
        assertEquals(20000, v.pesoCargaKg());
        assertEquals("2026-10-12", v.fecha());
        assertEquals(0L, v.version());
    }

    @Test
    void choferSinViajesRecibeListaVaciaYQuienNoEsChoferHabilitado403() throws Exception {
        assertTrue(servicio.viajes("44444444", "t").isEmpty());
        assertThrows(NoAutorizadoException.class, () -> servicio.viajes("22222222", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.viajes("99999999", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.viajes(null, "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.registrarEvento("22222222",
                evento(UUID.randomUUID().toString(), "GV-1", "INICIO"), "t"));
    }

    @Test
    void inicioPasaAEnCursoYElReenvioDevuelveElResultadoOriginalSinTocarLaGuia() throws Exception {
        String id = UUID.randomUUID().toString();
        ResultadoEvento r = enTx(() -> servicio.registrarEvento(CHOFER, evento(id, "GV-1", "INICIO"), "t"));
        assertEquals("EN_CURSO", r.estadoGuia());
        assertEquals(1L, r.version());
        assertFalse(r.duplicado());

        ResultadoEvento reenvio = enTx(() -> servicio.registrarEvento(CHOFER, evento(id.toUpperCase(), "GV-1", "INICIO"), "t"));
        assertTrue(reenvio.duplicado());
        assertEquals(r.estadoGuia(), reenvio.estadoGuia());
        assertEquals(r.version(), reenvio.version());
        assertEquals(r.recibido(), reenvio.recibido());
        em.clear();
        assertEquals(1L, em.find(Guia.class, "GV-1").getVersion());
        assertEquals(1, new EventoViajeDAOConEm().buscarPorGuia("GV-1").size());
        assertTrue(servicio.resultadoExistente(CHOFER, id).duplicado());
        assertNull(servicio.resultadoExistente("33333333", id)); // otro chofer no ve el evento
    }

    @Test
    void cicloCompletoDelViaje() throws Exception {
        assertEquals("EN_CURSO", registrar("GV-1", "INICIO").estadoGuia());
        assertEquals("EN_CURSO", registrar("GV-1", "CARGA_PARCIAL").estadoGuia());
        assertEquals("EN_CURSO", registrar("GV-1", "DESCARGA_PARCIAL").estadoGuia());
        ResultadoEvento fin = registrar("GV-1", "FIN");
        assertEquals("APLICADO", fin.resultado());
        assertEquals("FINALIZADA", fin.estadoGuia());
        assertEquals(2L, fin.version());
        // la finalizada ya no aparece en el movil
        assertEquals(List.of("GV-2"), servicio.viajes(CHOFER, "t").stream().map(ViajeVista::id).toList());
        em.clear();
        assertEquals(EstadoGuia.FINALIZADA, em.find(Guia.class, "GV-1").getEstado());
    }

    // Politica de conflictos, caso 3: el efecto ya estaba aplicado -> se acepta sin cambios
    @Test
    void eventoCuyoEfectoYaEstaAplicadoSeAceptaSinEfecto() throws Exception {
        registrar("GV-1", "INICIO");
        ResultadoEvento otroInicio = registrar("GV-1", "INICIO"); // p. ej. desde otro telefono
        assertEquals("SIN_EFECTO", otroInicio.resultado());
        assertEquals("VIAJE_YA_INICIADO", otroInicio.codigo());
        assertEquals("EN_CURSO", otroInicio.estadoGuia());
        assertEquals(1L, otroInicio.version()); // la guia no cambio
        registrar("GV-1", "FIN");
        ResultadoEvento otroFin = registrar("GV-1", "FIN");
        assertEquals("SIN_EFECTO", otroFin.resultado());
        assertEquals("VIAJE_YA_FINALIZADO", otroFin.codigo());
        assertEquals(4, new EventoViajeDAOConEm().buscarPorGuia("GV-1").size()); // quedan todos registrados
    }

    // Caso 4: evento imposible para el estado actual -> se guarda como CONFLICTO y la guia no cambia
    @Test
    void eventoImposibleSeGuardaComoConflictoSinTocarLaGuia() throws Exception {
        ResultadoEvento r = registrar("GV-1", "FIN");
        assertEquals("CONFLICTO", r.resultado());
        assertEquals("VIAJE_NO_INICIADO", r.codigo());
        assertEquals("ASIGNADA", r.estadoGuia());
        assertEquals("VIAJE_NO_INICIADO", registrar("GV-1", "CARGA_PARCIAL").codigo());
        registrar("GV-1", "INICIO");
        registrar("GV-1", "FIN");
        String id = UUID.randomUUID().toString();
        ResultadoEvento incidente = enTx(() -> servicio.registrarEvento(CHOFER, evento(id, "GV-1", "INCIDENTE"), "t"));
        assertEquals("CONFLICTO", incidente.resultado());
        assertEquals("VIAJE_FINALIZADO", incidente.codigo());
        // el reenvio del conflicto devuelve lo mismo
        ResultadoEvento reenvio = enTx(() -> servicio.registrarEvento(CHOFER, evento(id, "GV-1", "INCIDENTE"), "t"));
        assertTrue(reenvio.duplicado());
        assertEquals("CONFLICTO", reenvio.resultado());
        em.clear();
        EventoViaje guardado = em.find(EventoViaje.class, id);
        assertEquals(ResultadoEventoViaje.CONFLICTO, guardado.getResultado());
        assertEquals("VIAJE_FINALIZADO", guardado.getCodigo());
        assertEquals(EstadoGuia.FINALIZADA, em.find(Guia.class, "GV-1").getEstado());
        assertEquals(2L, em.find(Guia.class, "GV-1").getVersion()); // solo INICIO y FIN la cambiaron
    }

    // Caso 2: guia de su empresa pero asignada a otro chofer (reasignada) o cancelada -> CONFLICTO
    @Test
    void guiaReasignadaOCanceladaQuedaComoConflicto() throws Exception {
        ResultadoEvento r = registrar("GV-OTRO", "INICIO");
        assertEquals("CONFLICTO", r.resultado());
        assertEquals("GUIA_REASIGNADA", r.codigo());
        assertNull(r.guia()); // ya no es suya: no se le devuelven los datos
        assertEquals(EstadoGuia.ASIGNADA, em.find(Guia.class, "GV-OTRO").getEstado());

        enTx(() -> {
            em.find(Guia.class, "GV-2").cambiarEstado(EstadoGuia.CANCELADA);
            return null;
        });
        ResultadoEvento cancelada = registrar("GV-2", "INICIO");
        assertEquals("CONFLICTO", cancelada.resultado());
        assertEquals("GUIA_CANCELADA", cancelada.codigo());
        assertEquals(List.of("GV-1"), servicio.viajes(CHOFER, "t").stream().map(ViajeVista::id).toList());
    }

    // Caso 7: el movil manda la version que conocia; si la guia cambio se avisa y se mandan los datos actuales
    @Test
    void guiaModificadaSeDetectaPorVersion() throws Exception {
        ResultadoEvento r = enTx(() -> servicio.registrarEvento(CHOFER, eventoConVersion("GV-1", "INICIO", 0L), "t"));
        assertFalse(r.guiaModificada());
        assertEquals("EN_CURSO", r.guia().estado());
        assertEquals(1L, r.guia().version());

        // la empresa modifica la guia mientras el movil estaba sin conexion
        enTx(() -> {
            em.find(Guia.class, "GV-1").setDestino("Salto");
            return null;
        });
        ResultadoEvento carga = enTx(
                () -> servicio.registrarEvento(CHOFER, eventoConVersion("GV-1", "CARGA_PARCIAL", 1L), "t"));
        assertEquals("APLICADO", carga.resultado()); // el evento se acepta igual
        assertTrue(carga.guiaModificada());
        assertEquals("Salto", carga.guia().destino());
        assertEquals(2L, carga.guia().version());
        assertFalse(enTx(() -> servicio.registrarEvento(CHOFER, eventoConVersion("GV-1", "CARGA_PARCIAL", 2L), "t"))
                .guiaModificada());
    }

    @Test
    void guiaInexistenteODeOtraEmpresa404() {
        assertThrows(RecursoNoEncontradoException.class, () -> enTx(
                () -> servicio.registrarEvento(CHOFER, evento(UUID.randomUUID().toString(), "GV-NO", "INICIO"), "t")));
        // 44444444 es chofer solo de la empresa 1 y GV-2 es de la empresa 2
        assertThrows(RecursoNoEncontradoException.class, () -> enTx(() -> servicio.registrarEvento("44444444",
                evento(UUID.randomUUID().toString(), "GV-2", "INICIO"), "t")));
    }

    @Test
    void guiaSinAsignarDeSuEmpresaEsConflicto() throws Exception {
        assertEquals("GUIA_NO_ASIGNADA", registrar("GV-LIBRE", "INICIO").codigo());
    }

    @Test
    void validaciones() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.registrarEvento(CHOFER,
                new NuevoEvento("abc", " ", "DESPEGUE", "11/10/2026", null, 91.0, 0.0, "no-es-base64!", null), "t"));
        assertEquals(List.of("idEvento", "idGuia", "tipo", "fechaHora", "ubicacion", "foto").stream().sorted().toList(),
                e.getErrores().keySet().stream().sorted().toList(), e.getErrores().toString());

        e = assertThrows(DatosInvalidosException.class, () -> servicio.registrarEvento(CHOFER,
                new NuevoEvento(UUID.randomUUID().toString(), "GV-1", "INCIDENTE", HORA, "  ", null, null,
                        Base64.getEncoder().encodeToString("texto".getBytes()), null), "t"));
        assertTrue(e.getErrores().get("descripcion").contains("Describí"));
        assertTrue(e.getErrores().get("foto").contains("JPEG o PNG"));

        String grande = Base64.getEncoder().encodeToString(new byte[EventoViaje.MAX_FOTO_BYTES + 1]);
        e = assertThrows(DatosInvalidosException.class, () -> servicio.registrarEvento(CHOFER,
                new NuevoEvento(UUID.randomUUID().toString(), "GV-1", "INICIO", HORA, null, null, 1.0, grande, null),
                "t"));
        assertTrue(e.getErrores().get("foto").contains("5 MB"));
        assertTrue(e.getErrores().containsKey("ubicacion")); // latitud sin longitud
    }

    @Test
    void incidenteConFotoYUbicacionSeGuarda() throws Exception {
        registrar("GV-2", "INICIO");
        String id = UUID.randomUUID().toString();
        enTx(() -> servicio.registrarEvento(CHOFER, new NuevoEvento(id, "GV-2", "incidente", "2026-10-12T00:30:00Z",
                "Pinchadura en ruta 5", -34.9, -56.16,
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(JPEG), null), "t"));
        em.clear();
        EventoViaje guardado = em.find(EventoViaje.class, id);
        assertEquals("Pinchadura en ruta 5", guardado.getDescripcion());
        assertArrayEquals(JPEG, guardado.getFoto());
        assertEquals(-34.9, guardado.getLatitud());
        assertEquals(CHOFER, guardado.getCiChofer());
        assertEquals("2026-10-12T00:30:00Z", guardado.getFechaHoraDispositivo().toString());
        assertEquals(EstadoGuia.EN_CURSO, guardado.getEstadoGuiaResultante());
        assertEquals(ResultadoEventoViaje.APLICADO, guardado.getResultado());
    }

    @Test
    void reglasDeDecision() {
        assertEquals(ResultadoEventoViaje.APLICADO,
                ViajesChoferService.decidir(TipoEventoViaje.INICIO, EstadoGuia.ASIGNADA).resultado());
        assertEquals(EstadoGuia.EN_CURSO,
                ViajesChoferService.decidir(TipoEventoViaje.INICIO, EstadoGuia.ASIGNADA).nuevoEstado());
        assertEquals(EstadoGuia.FINALIZADA,
                ViajesChoferService.decidir(TipoEventoViaje.FIN, EstadoGuia.EN_CURSO).nuevoEstado());
        assertEquals("GUIA_NO_ASIGNADA",
                ViajesChoferService.decidir(TipoEventoViaje.INICIO, EstadoGuia.SIN_ASIGNAR).codigo());
        assertEquals("GUIA_CANCELADA",
                ViajesChoferService.decidir(TipoEventoViaje.INCIDENTE, EstadoGuia.CANCELADA).codigo());
        assertEquals(ResultadoEventoViaje.CONFLICTO,
                ViajesChoferService.decidir(TipoEventoViaje.INICIO, EstadoGuia.FINALIZADA).resultado());
    }

    // ===== helpers =====

    private final class EventoViajeDAOConEm {
        List<EventoViaje> buscarPorGuia(String idGuia) throws Exception {
            return conEm(new EventoViajeDAO()).buscarPorGuia(em.find(Guia.class, idGuia));
        }
    }

    private ResultadoEvento registrar(String guia, String tipo) throws Exception {
        return enTx(() -> servicio.registrarEvento(CHOFER, evento(UUID.randomUUID().toString(), guia, tipo), "t"));
    }

    private static NuevoEvento evento(String id, String guia, String tipo) {
        return new NuevoEvento(id, guia, tipo, HORA, tipo.equals("INCIDENTE") ? "algo" : null, null, null, null, null);
    }

    private static NuevoEvento eventoConVersion(String guia, String tipo, Long version) {
        return new NuevoEvento(UUID.randomUUID().toString(), guia, tipo, HORA, null, null, null, null, version);
    }

    private static Empresa empresa(Long nro, String nombre) {
        Empresa e = new Empresa(nro, nombre, nombre + " S.A.", "Montevideo", null);
        e.setHabilitada(true);
        return e;
    }

    private static Guia guia(String id, Empresa empresa, Vehiculo vehiculo, Chofer chofer, LocalDate fecha) {
        Guia g = new Guia(id, "Agropecuario", 20000, fecha, "Montevideo", "Paysandú", empresa);
        g.setTipoCarga("Granos a granel");
        g.asignar(vehiculo, chofer);
        return g;
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
