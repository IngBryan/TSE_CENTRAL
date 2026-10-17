package tse.lab.frontofficefuncionarios.service.gestioncasos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.data.FuncionarioDAO;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.frontofficefuncionarios.service.ConflictoException;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.CasoDetalle;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.CasoResumen;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.DescargoVista;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.EvidenciaVista;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.PaginaCasos;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.Resolucion;
import tse.lab.frontofficefuncionarios.service.gestioncasos.GestionCasosService.ResolucionRegistrada;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.Descargo;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.FundamentoResolucion;
import tse.lab.model.Funcionario;
import tse.lab.model.Guia;
import tse.lab.model.ITV;
import tse.lab.model.NodoBalanza;
import tse.lab.model.PermisoCirculacion;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;

// CU Gestionar casos de fiscalizacion contra H2 con los DAOs reales.
class GestionCasosServiceTest {

    private static final String FUNCIONARIO = "45678901";
    private static final String NO_FUNCIONARIO = "54074380";

    private EntityManagerFactory emf;
    private EntityManager em;
    private GestionCasosService servicio;
    private Long detectado;
    private Long notificado;
    private Long enDescargo;
    private Long enDescargoSinDescargo;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        AutorizacionFuncionario autorizacion = new AutorizacionFuncionario();
        set(autorizacion, "funcionarios", conEm(new FuncionarioDAO()));
        servicio = new GestionCasosService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "casos", conEm(new CasoFiscalizacionDAO()));
        set(servicio, "auditoria", conEm(new AuditoriaCasoDAO()));

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        Vehiculo vehiculo = new Vehiculo("SBA 1234", "Scania", "R 450", 9000, 26000, empresa);
        Guia guia = new Guia("GV-1", "Agropecuario", 18000, LocalDate.of(2026, 10, 10), "Montevideo", "Paysandú",
                empresa);
        guia.setTipoCarga("Granos a granel");
        guia.setVehiculo(vehiculo);
        NodoBalanza balanza = new NodoBalanza("Peaje Pando", "Ruta 8 km 32", 1L);
        EventoPesajes pesaje = new EventoPesajes(1001L, "SBA 1234", Instant.parse("2026-10-10T12:00:00Z"), 38500,
                balanza);

        CasoFiscalizacion cDetectado = new CasoFiscalizacion("Exceso de carga", "EXCESO_CARGA", guia);
        cDetectado.getEventosPesajes().add(pesaje);
        CasoFiscalizacion cNotificado = caso("Viaje sin guia", guia, EstadoCaso.NOTIFICADA);
        CasoFiscalizacion cEnDescargo = caso("Exceso de carga", guia, EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO);
        cEnDescargo.getEventosPesajes().add(pesaje);
        CasoFiscalizacion cSinDescargo = caso("Desvio de ruta", guia, EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO);

        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(vehiculo);
        em.persist(guia);
        em.persist(new PermisoCirculacion("PNC-1", LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1), vehiculo));
        em.persist(new ITV(LocalDate.of(2025, 3, 1), LocalDate.of(2026, 3, 1), vehiculo));
        em.persist(new ITV(LocalDate.of(2026, 3, 1), LocalDate.of(2027, 3, 1), vehiculo));
        em.persist(balanza);
        em.persist(pesaje);
        em.persist(new Funcionario(FUNCIONARIO, "funcionario@mtop.gub.uy"));
        em.persist(cDetectado);
        em.persist(cNotificado);
        em.persist(cEnDescargo);
        em.persist(cSinDescargo);
        em.persist(new Descargo("La balanza estaba descalibrada", "EXCESO_CARGA", "PRESENTADO", cEnDescargo));
        em.getTransaction().commit();
        em.clear();
        detectado = cDetectado.getId();
        notificado = cNotificado.getId();
        enDescargo = cEnDescargo.getId();
        enDescargoSinDescargo = cSinDescargo.getId();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    // ===== FA1: usuario no autorizado =====

    @Test
    void quienNoEsFuncionarioNoAccedeANingunaOperacion() {
        for (String ci : new String[] { NO_FUNCIONARIO, null, " " }) {
            assertThrows(NoAutorizadoException.class, () -> servicio.bandeja(ci, null, null, null, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.detalle(ci, enDescargo, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.evidencia(ci, enDescargo, "t"));
            assertThrows(NoAutorizadoException.class, () -> servicio.descargo(ci, enDescargo, "t"));
            assertThrows(NoAutorizadoException.class,
                    () -> enTx(() -> servicio.resolver(ci, enDescargo, new Resolucion("CONFIRMAR", "x"), "t")));
        }
        assertSinCambios(enDescargo, EstadoCaso.EN_DESCARGO);
    }

    // ===== Flujo principal: bandeja y detalle =====

    @Test
    void bandejaListaLosCasosYFiltraPorEstado() throws Exception {
        List<CasoResumen> todos = bandeja(null);
        assertEquals(4, todos.size());
        CasoResumen r = todos.stream().filter(c -> c.id().equals(enDescargo)).findFirst().orElseThrow();
        assertEquals("EN_DESCARGO", r.estado());
        assertEquals("GV-1", r.idGuia());
        assertEquals("Transportes del Este", r.empresa());
        assertEquals("SBA 1234", r.matricula());
        assertTrue(r.tieneDescargo());
        assertTrue(r.puedeResolverse());

        List<CasoResumen> enDescargoLista = bandeja(" en_descargo ");
        assertEquals(2, enDescargoLista.size());
        assertTrue(enDescargoLista.stream().allMatch(c -> c.estado().equals("EN_DESCARGO")));
        assertEquals(List.of(detectado), bandeja("DETECTADA").stream().map(CasoResumen::id).toList());
        assertTrue(assertThrows(DatosInvalidosException.class, () -> bandeja("ABIERTO"))
                .getErrores().containsKey("estado"));
    }

    @Test
    void bandejaPaginada() throws Exception {
        PaginaCasos primera = servicio.bandeja(FUNCIONARIO, null, null, null, "t");
        assertEquals(1, primera.pagina());
        assertEquals(GestionCasosService.TAMANIO_POR_DEFECTO, primera.tamanio());
        assertEquals(4, primera.total());
        assertEquals(1, primera.totalPaginas());

        PaginaCasos p1 = servicio.bandeja(FUNCIONARIO, null, "1", "3", "t");
        PaginaCasos p2 = servicio.bandeja(FUNCIONARIO, null, "2", "3", "t");
        assertEquals(3, p1.casos().size());
        assertEquals(1, p2.casos().size());
        assertEquals(2, p1.totalPaginas());
        assertEquals(4, p2.total());
        // Las paginas no se superponen y juntas son la bandeja completa, en el mismo orden
        List<Long> ids = new ArrayList<>(p1.casos().stream().map(CasoResumen::id).toList());
        ids.addAll(p2.casos().stream().map(CasoResumen::id).toList());
        assertEquals(bandeja(null).stream().map(CasoResumen::id).toList(), ids);

        // Con filtro el total es el del estado
        PaginaCasos filtrada = servicio.bandeja(FUNCIONARIO, "EN_DESCARGO", "1", "1", "t");
        assertEquals(1, filtrada.casos().size());
        assertEquals(2, filtrada.total());
        assertEquals(2, filtrada.totalPaginas());

        // Despues de la ultima pagina: vacia, pero con el total
        PaginaCasos fuera = servicio.bandeja(FUNCIONARIO, null, "99", "3", "t");
        assertTrue(fuera.casos().isEmpty());
        assertEquals(4, fuera.total());

        PaginaCasos vacia = servicio.bandeja(FUNCIONARIO, "CONFIRMADA", null, null, "t");
        assertTrue(vacia.casos().isEmpty());
        assertEquals(0, vacia.totalPaginas());
    }

    @Test
    void bandejaRechazaPaginacionInvalida() {
        for (String pagina : new String[] { "0", "-1", "dos", "1.5" }) {
            assertTrue(assertThrows(DatosInvalidosException.class,
                    () -> servicio.bandeja(FUNCIONARIO, null, pagina, null, "t")).getErrores().containsKey("pagina"));
        }
        for (String tamanio : new String[] { "0", "101", "x" }) {
            assertTrue(assertThrows(DatosInvalidosException.class,
                    () -> servicio.bandeja(FUNCIONARIO, null, null, tamanio, "t")).getErrores().containsKey("tamanio"));
        }
        // Informa todos los errores juntos
        assertEquals(3, assertThrows(DatosInvalidosException.class,
                () -> servicio.bandeja(FUNCIONARIO, "ABIERTO", "0", "0", "t")).getErrores().size());
    }

    // Todos los casos (o los del estado) en una sola pagina
    private List<CasoResumen> bandeja(String estado) throws Exception {
        return servicio.bandeja(FUNCIONARIO, estado, "1", String.valueOf(GestionCasosService.TAMANIO_MAXIMO), "t")
                .casos();
    }

    @Test
    void detalleMuestraEstadoYSiAdmiteResolucion() throws Exception {
        CasoDetalle d = servicio.detalle(FUNCIONARIO, enDescargo, "t");
        assertEquals("EN_DESCARGO", d.estado());
        assertTrue(d.puedeResolverse());
        assertTrue(d.tieneDescargo());
        assertEquals(List.of("CONFIRMADA", "DESESTIMADA"), d.estadosSiguientes());
        assertEquals("Montevideo", d.guia().origen());
        assertNull(d.resolucion());
        assertTrue(d.historial().isEmpty());

        CasoDetalle n = servicio.detalle(FUNCIONARIO, notificado, "t");
        assertFalse(n.puedeResolverse());
        assertFalse(n.tieneDescargo());
    }

    @Test
    void casoInexistente() {
        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.detalle(FUNCIONARIO, 9999L, "t"));
        assertEquals("CASO_NO_ENCONTRADO", e.getCodigo());
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.evidencia(FUNCIONARIO, null, "t"));
        assertEquals("CASO_NO_ENCONTRADO", assertThrows(RecursoNoEncontradoException.class,
                () -> enTx(() -> servicio.resolver(FUNCIONARIO, 9999L, new Resolucion("CONFIRMAR", "x"), "t")))
                .getCodigo());
    }

    // ===== FA2: evidencia =====

    @Test
    void evidenciaMuestraPesajeContraCapacidadDeclaradaYHabilitacion() throws Exception {
        EvidenciaVista ev = servicio.evidencia(FUNCIONARIO, enDescargo, "t");
        assertEquals("EXCESO_DE_CARGA", ev.tipoCaso());
        assertEquals("GV-1", ev.guia().id());
        assertEquals(35000, ev.vehiculo().pesoMaximoKg()); // 9000 + 26000
        assertEquals("PNC-1", ev.vehiculo().nroPermiso());
        assertEquals("2027-01-01", ev.vehiculo().vencimientoPermiso());
        assertEquals("2027-03-01", ev.vehiculo().vencimientoItv()); // la ITV mas reciente
        assertEquals(1, ev.pesajes().size());
        assertEquals(38500, ev.pesajes().get(0).pesoKg());
        assertEquals(3500, ev.pesajes().get(0).excesoKg());
        assertEquals("Peaje Pando", ev.pesajes().get(0).balanza());
        assertSinCambios(enDescargo, EstadoCaso.EN_DESCARGO);
    }

    @Test
    void evidenciaMuestraLaItvVigenteAlDetectarse() throws Exception {
        Vehiculo vehiculo = em.find(Vehiculo.class, "SBA 1234");
        LocalDate hoy = LocalDate.now();
        em.getTransaction().begin();
        // Renovada despues de la deteccion: no cuenta. Sin vencimiento: no gana por el orden de los nulos.
        em.persist(new ITV(hoy.plusDays(1), LocalDate.of(2028, 1, 1), vehiculo));
        em.persist(new ITV(LocalDate.of(2026, 6, 1), null, vehiculo));
        em.getTransaction().commit();
        em.clear();

        assertEquals("2027-03-01", servicio.evidencia(FUNCIONARIO, enDescargo, "t").vehiculo().vencimientoItv());
    }

    // ===== FA3 y FA4: descargo =====

    @Test
    void descargoPresentadoPorLaEmpresa() throws Exception {
        DescargoVista d = servicio.descargo(FUNCIONARIO, enDescargo, "t");
        assertEquals("La balanza estaba descalibrada", d.descripcion());
        assertEquals(enDescargo, d.idCaso());
    }

    @Test
    void sinDescargoSeInformaYElCasoNoCambia() {
        RecursoNoEncontradoException e = assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.descargo(FUNCIONARIO, notificado, "t"));
        assertEquals("SIN_DESCARGO", e.getCodigo());
        assertSinCambios(notificado, EstadoCaso.NOTIFICADA);
    }

    // ===== FA5: resolver =====

    @Test
    void confirmarRegistraResolucionFundamentoYAuditoria() throws Exception {
        ResolucionRegistrada r = enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo,
                new Resolucion(" confirmar ", "  Pesaje de 38.500 kg supera los 35.000 kg declarados.  "), "corr-1"));
        assertEquals("La resolución fue registrada.", r.mensaje());
        assertEquals("CONFIRMADA", r.caso().estado());
        assertFalse(r.caso().puedeResolverse());
        em.clear();

        assertEquals(EstadoCaso.CONFIRMADA, em.find(CasoFiscalizacion.class, enDescargo).getEstado());
        FundamentoResolucion f = em.createQuery("SELECT f FROM FundamentoResolucion f", FundamentoResolucion.class)
                .getSingleResult();
        assertEquals("Pesaje de 38.500 kg supera los 35.000 kg declarados.", f.getDescripcion());
        assertEquals(FUNCIONARIO, f.getFuncionario().getCi());
        assertNotNull(f.getFechaHora());

        List<RegistroAuditoriaCaso> pista = auditoria(enDescargo);
        assertEquals(1, pista.size());
        RegistroAuditoriaCaso a = pista.get(0);
        assertEquals(FUNCIONARIO, a.getUsuario());
        assertEquals(AccionAuditoriaCaso.RESOLUCION, a.getAccion());
        assertEquals(EstadoCaso.EN_DESCARGO, a.getEstadoAnterior());
        assertEquals(EstadoCaso.CONFIRMADA, a.getEstadoNuevo());
        assertEquals(f.getDescripcion(), a.getFundamento());
        assertEquals("corr-1", a.getCorrelationId());
        assertNotNull(a.getFechaHora());

        CasoDetalle d = servicio.detalle(FUNCIONARIO, enDescargo, "t");
        assertEquals("CONFIRMAR", d.resolucion().decision());
        assertEquals(FUNCIONARIO, d.resolucion().ciFuncionario());
        assertEquals(1, d.historial().size());
        assertEquals("RESOLUCION", d.historial().get(0).accion());
    }

    @Test
    void desestimarDejaElCasoDesestimado() throws Exception {
        ResolucionRegistrada r = enTx(() -> servicio.resolver(FUNCIONARIO, enDescargoSinDescargo,
                new Resolucion("DESESTIMAR", "El desvio se debio a un corte de ruta."), "t"));
        assertEquals("DESESTIMADA", r.caso().estado());
        assertEquals("DESESTIMAR", r.caso().resolucion().decision());
        em.clear();
        assertEquals(EstadoCaso.DESESTIMADA, em.find(CasoFiscalizacion.class, enDescargoSinDescargo).getEstado());
        assertEquals(EstadoCaso.DESESTIMADA, auditoria(enDescargoSinDescargo).get(0).getEstadoNuevo());
    }

    // ===== FA6: transicion no permitida =====

    @Test
    void noSeResuelveUnCasoQueNoEstaEnDescargo() throws Exception {
        for (Long id : List.of(detectado, notificado)) {
            EstadoCaso antes = em.find(CasoFiscalizacion.class, id).getEstado();
            for (String decision : List.of("CONFIRMAR", "DESESTIMAR")) {
                ConflictoException e = assertThrows(ConflictoException.class, () -> enTx(
                        () -> servicio.resolver(FUNCIONARIO, id, new Resolucion(decision, "fundamento"), "t")));
                assertEquals("TRANSICION_NO_PERMITIDA", e.getCodigo());
            }
            assertSinCambios(id, antes);
        }
    }

    @Test
    void unCasoResueltoNoSePuedeVolverAResolver() throws Exception {
        enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo, new Resolucion("CONFIRMAR", "primera"), "t"));
        for (String decision : List.of("CONFIRMAR", "DESESTIMAR")) {
            assertEquals("TRANSICION_NO_PERMITIDA", assertThrows(ConflictoException.class, () -> enTx(
                    () -> servicio.resolver(FUNCIONARIO, enDescargo, new Resolucion(decision, "segunda"), "t")))
                    .getCodigo());
        }
        em.clear();
        assertEquals(EstadoCaso.CONFIRMADA, em.find(CasoFiscalizacion.class, enDescargo).getEstado());
        assertEquals(1, auditoria(enDescargo).size());
        assertEquals("primera", em.createQuery("SELECT f.descripcion FROM FundamentoResolucion f", String.class)
                .getSingleResult());
    }

    @Test
    void datosInvalidosNoModificanElCaso() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo, new Resolucion("APROBAR", "   "), "t")));
        assertTrue(e.getErrores().containsKey("decision"));
        assertTrue(e.getErrores().containsKey("fundamento"));
        assertTrue(assertThrows(DatosInvalidosException.class,
                () -> enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo,
                        new Resolucion("CONFIRMAR", "x".repeat(RegistroAuditoriaCaso.MAX_FUNDAMENTO + 1)), "t")))
                .getErrores().get("fundamento").contains("2000"));
        assertThrows(DatosInvalidosException.class,
                () -> enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo, null, "t")));
        assertSinCambios(enDescargo, EstadoCaso.EN_DESCARGO);
    }

    // ===== Pista de auditoria de solo agregado =====

    @Test
    void laPistaDeAuditoriaNoSePuedeBorrar() throws Exception {
        enTx(() -> servicio.resolver(FUNCIONARIO, enDescargo, new Resolucion("CONFIRMAR", "fundamento"), "t"));
        em.clear();
        em.getTransaction().begin();
        RegistroAuditoriaCaso registro = auditoria(enDescargo).get(0);
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            em.remove(registro);
            em.flush();
        });
        em.getTransaction().rollback();
        assertTrue(causa(e, UnsupportedOperationException.class), e.toString());
        em.clear();
        assertEquals(1, auditoria(enDescargo).size());
    }

    // ===== helpers =====

    // Lo consultado o rechazado no cambio el estado del caso, ni genero resolucion ni auditoria
    private void assertSinCambios(Long idCaso, EstadoCaso esperado) {
        em.clear();
        assertEquals(esperado, em.find(CasoFiscalizacion.class, idCaso).getEstado());
        assertTrue(auditoria(idCaso).isEmpty());
        assertEquals(0L, em.createQuery("SELECT COUNT(f) FROM FundamentoResolucion f WHERE f.casoFiscalizacion.id = :id",
                Long.class).setParameter("id", idCaso).getSingleResult());
    }

    private List<RegistroAuditoriaCaso> auditoria(Long idCaso) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id ORDER BY r.id",
                RegistroAuditoriaCaso.class).setParameter("id", idCaso).getResultList();
    }

    private static CasoFiscalizacion caso(String descripcion, Guia guia, EstadoCaso... camino) {
        CasoFiscalizacion c = new CasoFiscalizacion(descripcion, descripcion.toUpperCase().replace(' ', '_'), guia);
        for (EstadoCaso e : camino) {
            c.cambiarEstado(e);
        }
        return c;
    }

    private static boolean causa(Throwable t, Class<? extends Throwable> tipo) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (tipo.isInstance(c)) {
                return true;
            }
        }
        return false;
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    // Emula la transaccion del contenedor: commit si termina bien, rollback si tira excepcion
    private <T> T enTx(Accion<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T r = accion.ejecutar();
            em.getTransaction().commit();
            return r;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
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
