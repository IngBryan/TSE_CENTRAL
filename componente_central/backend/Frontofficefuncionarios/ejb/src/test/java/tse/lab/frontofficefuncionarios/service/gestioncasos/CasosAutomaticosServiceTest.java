package tse.lab.frontofficefuncionarios.service.gestioncasos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.NoOpTransactions;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.data.CirculacionSinGuiaDAO;
import tse.lab.model.CirculacionSinGuia;
import tse.lab.model.EstadoCirculacionSinGuia;
import tse.lab.model.MotivoFinCirculacionSinGuia;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.ClaveEvento;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EventoPesajes;
import tse.lab.model.Guia;
import tse.lab.model.NodoBalanza;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;
import tse.lab.model.EvidenciaHabilitacion;

// Operaciones de la fiscalizacion automatica sobre los casos (EJB remoto) contra H2 con los DAOs reales.
class CasosAutomaticosServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-17T10:00:10Z");
    private static PruebaDesvioRemota prueba(Instant ts) {
        return new PruebaDesvioRemota(ts.plusSeconds(10), -34.9, -56.1, -34.9, -56.12,
                2400.0, 2600.0, 2000.0, 30L, List.of("sha-ruta-a", "sha-ruta-b"));
    }

    private static final EventoFiscalizado TRACKING_T1 = new EventoFiscalizado("SBC1234", T1, OrigenEvento.TRACKING);
    private static final EventoFiscalizado TRACKING_T2 = new EventoFiscalizado("SBC1234", T2, OrigenEvento.TRACKING);
    private static final EventoFiscalizado PESAJE_T1 = new EventoFiscalizado("SBC1234", T1, OrigenEvento.PESAJE);

    private EntityManagerFactory emf;
    private EntityManager em;
    private CasosAutomaticosService servicio;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficefuncionariosTestPersistenceUnit");
        em = emf.createEntityManager();
        servicio = new CasosAutomaticosService();
        set(servicio, "casos", conEm(new CasoFiscalizacionDAO()));
        set(servicio, "auditoria", conEm(NoOpTransactions.wired(new AuditoriaCasoDAO())));
        set(servicio, "circulaciones", conEm(new CirculacionSinGuiaDAO()));

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        Vehiculo vehiculo = new Vehiculo("SBC1234", "Mercedes-Benz", "Actros", 8500, 25000, empresa);
        Guia guia = new Guia("GV-1", "Agropecuario", 20000, LocalDate.of(2026, 10, 17), "Mercedes", "Nueva Palmira",
                empresa);
        guia.setVehiculo(vehiculo);
        NodoBalanza balanza = new NodoBalanza("Ruta 8 - Km 150", "Ruta 8 km 150", 4L);
        enTx(() -> {
            em.persist(empresa);
            em.persist(vehiculo);
            em.persist(guia);
            em.persist(balanza);
            em.persist(new EventoPesajes(3L, "SBC1234", T1, 38_000, balanza));
            return null;
        });
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    private static EvidenciaHabilitacion sinDocumentos(Instant fecha) {
        return new EvidenciaHabilitacion("SBC1234", fecha, null, null, null, null, null);
    }

    @Test
    void conservaRevisionYPesoBaseDeLaEvidenciaAnteUnReintento() {
        var e=new EventoFiscalizado("SBC1234",T1,OrigenEvento.PESAJE,"c",2L,33500L);
        DatosCaso caso=enTx(()->servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA,"GV-1",e));
        var repetido=new EventoFiscalizado("SBC1234",T1,OrigenEvento.PESAJE,"c",3L,34000L);
        enTx(()->servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA,"GV-1",repetido));
        var evidencias=caso(caso.getId()).getEvidenciasReglas();
        assertEquals(1,evidencias.size());
        assertEquals(2,evidencias.getFirst().getRevision());
        assertEquals(33500L,evidencias.getFirst().getPesoBaseKg());
    }

    @Test
    void conservaPruebaDocumentalPorEventoYOrigenSinDuplicarTrasElDescargo() {
        var prueba = sinDocumentos(T1);
        DatosCaso creado = enTx(() -> servicio.registrarHabilitacion("GV-1", TRACKING_T1, prueba));
        enTx(() -> servicio.registrarHabilitacion("GV-1", TRACKING_T1, prueba));
        enTx(() -> servicio.registrarHabilitacion("GV-1", PESAJE_T1, prueba));
        assertEquals(2, caso(creado.getId()).getEvidenciasHabilitacion().size());
        enTx(() -> {
            var c = em.find(CasoFiscalizacion.class, creado.getId());
            c.cambiarEstado(EstadoCaso.NOTIFICADA);
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            return null;
        });
        var repetido = enTx(() -> servicio.registrarHabilitacion("GV-1", TRACKING_T1, prueba));
        assertEquals(creado.getId(), repetido.getId());
        assertEquals(2, caso(creado.getId()).getEvidenciasHabilitacion().size());
        assertEquals(prueba.conOrigen("TRACKING"), caso(creado.getId()).getEvidenciasHabilitacion()
                .stream().filter(p -> p.getOrigen().equals("TRACKING")).findFirst().orElseThrow());
    }

    @Test
    void rechazaHabilitacionSinPruebaOConPruebaAjenaOConDocumentosVigentes() {
        assertThrows(IllegalArgumentException.class, () -> enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, null, TRACKING_T1)));
        assertThrows(IllegalArgumentException.class, () -> enTx(() -> servicio.registrarHabilitacion(
                null, TRACKING_T1, null)));
        assertThrows(IllegalArgumentException.class, () -> enTx(() -> servicio.registrarHabilitacion(
                null, TRACKING_T1, sinDocumentos(T2))));
        var vigente = new EvidenciaHabilitacion("SBC1234", T1, "PNC", LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1), LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> enTx(() -> servicio.registrarHabilitacion(
                null, TRACKING_T1, vigente)));
    }

    private CasoFiscalizacion caso(long id) {
        em.clear();
        return em.find(CasoFiscalizacion.class, id);
    }

    private List<RegistroAuditoriaCaso> auditoria(long idCaso) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id ORDER BY r.id",
                RegistroAuditoriaCaso.class).setParameter("id", idCaso).getResultList();
    }

    private static PosicionSinGuiaRemota gps(Instant fecha, double lat, double lon) {
        return new PosicionSinGuiaRemota("SBC1234", fecha, lat, lon);
    }
    private DatosCaso iniciarCirculacion() {
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        return enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -34.9, -56.098)));
    }
    private CirculacionSinGuia circulacion(long idCaso) {
        em.clear();
        return em.createQuery("SELECT c FROM CirculacionSinGuia c WHERE c.caso.id = :id", CirculacionSinGuia.class)
                .setParameter("id", idCaso).getSingleResult();
    }

    @Test
    void creaElCasoDetectadoConElVehiculoYElEventoComoEvidencia() {
        DatosCaso creado = iniciarCirculacion();

        assertEquals("DETECTADA", creado.getEstado());
        CasoFiscalizacion c = caso(creado.getId());
        assertEquals("VIAJE_SIN_GUIA", c.getTipoCaso());
        assertEquals("SBC1234", c.getVehiculo().getMatricula());
        assertNull(c.getGuia());
        assertEquals(2, c.getClavesEventosTracking().size());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(creado.getId()).getEstado());
        RegistroAuditoriaCaso creacion = auditoria(creado.getId()).get(0);
        assertEquals(RegistroAuditoriaCaso.SISTEMA, creacion.getUsuario());
        assertEquals(AccionAuditoriaCaso.CREACION, creacion.getAccion());
        assertEquals(EstadoCaso.DETECTADA, creacion.getEstadoNuevo());
    }

    @Test
    void unCasoConGuiaQuedaAsociadoAEllaYAlPesaje() {
        var evento = new EventoFiscalizado("SBC1234", T1, OrigenEvento.PESAJE, "c-pesaje");
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-1", evento));

        CasoFiscalizacion c = caso(creado.getId());
        assertEquals("GV-1", c.getGuia().getId());
        assertEquals(1, c.getEventosPesajes().size());
        assertEquals("c-pesaje", auditoria(creado.getId()).getFirst().getCorrelationId());
    }

    @Test
    void notificarPasaElCasoANotificadaYQuedaEnLaAuditoriaComoSistema() {
        DatosCaso creado = iniciarCirculacion();

        DatosCaso notificado = enTx(() -> servicio.notificarCaso(creado.getId(), "c-1"));

        assertEquals("NOTIFICADA", notificado.getEstado());
        RegistroAuditoriaCaso cambio = auditoria(creado.getId()).get(1);
        assertEquals(RegistroAuditoriaCaso.SISTEMA, cambio.getUsuario());
        assertEquals(AccionAuditoriaCaso.CAMBIO_ESTADO, cambio.getAccion());
        assertEquals(EstadoCaso.DETECTADA, cambio.getEstadoAnterior());
        assertEquals(EstadoCaso.NOTIFICADA, cambio.getEstadoNuevo());
    }

    @ParameterizedTest(name = "CL-G12: notificar nuevamente desde {0}")
    @EnumSource(value = EstadoCaso.class, names = "DETECTADA", mode = EnumSource.Mode.EXCLUDE)
    void notificarUnCasoQueYaNoEstaDetectadoNoHaceNada(EstadoCaso estado) {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));
        enTx(() -> servicio.notificarCaso(creado.getId(), "c-1"));
        enTx(() -> {
            var c = em.find(CasoFiscalizacion.class, creado.getId());
            if (estado != EstadoCaso.NOTIFICADA) c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            if (estado.esFinal()) c.cambiarEstado(estado);
            return null;
        });
        var registrosPrevios = auditoria(creado.getId()).stream().map(RegistroAuditoriaCaso::getId).toList();

        DatosCaso otraVez = enTx(() -> servicio.notificarCaso(creado.getId(), "repetida"));

        assertEquals(creado.getId(), otraVez.getId());
        assertEquals(estado.name(), otraVez.getEstado());
        assertEquals(estado, caso(creado.getId()).getEstado());
        assertEquals(1, caso(creado.getId()).getEventosPesajes().size());
        assertEquals(registrosPrevios,
                auditoria(creado.getId()).stream().map(RegistroAuditoriaCaso::getId).toList());
    }

    @Test
    void losCasosAbiertosSonLosDetectadosYNotificados() {
        DatosCaso detectado = iniciarCirculacion();
        DatosCaso enDescargo = enTx(() -> servicio.registrarHabilitacion(null, TRACKING_T2, sinDocumentos(T2)));
        enTx(() -> {
            CasoFiscalizacion c = em.find(CasoFiscalizacion.class, enDescargo.getId());
            c.cambiarEstado(EstadoCaso.NOTIFICADA);
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            return null;
        });

        assertEquals(List.of(detectado), enTx(() -> servicio.casosAbiertos("SBC1234")));
    }

    @Test
    void variasInfraccionesCompartenGpsSinDuplicarloEnCadaCaso() {
        DatosCaso sinGuia = iniciarCirculacion();
        DatosCaso habilitacion = enTx(() -> servicio.registrarHabilitacion(null, TRACKING_T1, sinDocumentos(T1)));
        DatosCaso repetido = enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -34.9, -56.098)));
        assertEquals(sinGuia.getId(), repetido.getId());
        assertEquals(2, caso(sinGuia.getId()).getClavesEventosTracking().size());
        assertEquals(1, caso(habilitacion.getId()).getClavesEventosTracking().size());

        ClaveEvento evidenciaEsperada = new ClaveEvento("SBC1234", T1);
        assertEquals(
            evidenciaEsperada,
            caso(sinGuia.getId()).getClavesEventosTracking().get(0)
        );

        assertEquals(
            evidenciaEsperada,
            caso(habilitacion.getId()).getClavesEventosTracking().get(0)
        );
    }

    @Test
    void unaBrechaEntreMedicionesNoSeparaViajesSinGuiaEnDistintosCasos() {
        DatosCaso primero = iniciarCirculacion();
        enTx(() -> servicio.notificarCaso(primero.getId(), "c-1"));
        Instant tarde = T1.plusSeconds(3600);
        DatosCaso retorno = enTx(() -> servicio.registrarCirculacionSinGuia(gps(tarde, -34.8, -56.0)));
        DatosCaso siguiente = enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(tarde.plusSeconds(10), -34.8, -55.998)));
        assertEquals(primero.getId(), retorno.getId());
        assertEquals(primero.getId(), siguiente.getId());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(primero.getId()).getEstado());
        assertNull(circulacion(primero.getId()).getMotivoFin());
        assertNull(circulacion(primero.getId()).getFechaFin());
        assertEquals(4, caso(primero.getId()).getClavesEventosTracking().size());
        assertEquals(1, enTx(() -> servicio.casosAbiertos("SBC1234")).size());
        // Una medicion atrasada dentro de la antigua brecha pertenece al mismo caso.
        assertEquals(primero.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(T1.plusSeconds(1800), -34.85, -56.05))).getId());
        // Reentregar una evidencia ya asociada no la duplica.
        assertEquals(primero.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(T2, -34.9, -56.098))).getId());
        assertEquals(5, caso(primero.getId()).getClavesEventosTracking().size());
    }

    @Test
    void variosViajesTrasDiasSinGpsSiguenAcumulandoEvidenciaEnElMismoCaso() {
        DatosCaso creado = iniciarCirculacion();
        for (int dia = 1; dia <= 3; dia++) {
            Instant salida = T1.plusSeconds(dia * 86400L);
            assertEquals(creado.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                    gps(salida, -34.9, -56.1))).getId());
            assertEquals(creado.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                    gps(salida.plusSeconds(10), -34.9, -56.098))).getId());
        }
        assertEquals(8, caso(creado.getId()).getClavesEventosTracking().size());
        CirculacionSinGuia actual = circulacion(creado.getId());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, actual.getEstado());
        assertEquals(T1, actual.getFechaInicio());
        assertEquals(T2.plusSeconds(3 * 86400L), actual.getUltimaMedicion());
        assertNull(actual.getFechaFin());
        assertEquals(1L, em.createQuery("SELECT count(c) FROM CirculacionSinGuia c", Long.class).getSingleResult());
    }

    @Test
    void laVentanaParaConfirmarElMovimientoInicialSeConserva() {
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        Instant tarde = T1.plusSeconds(86400);
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(tarde, -34.8, -56.0))));
        assertEquals(List.of(), enTx(() -> servicio.casosAbiertos("SBC1234")));
        DatosCaso creado = enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(tarde.plusSeconds(10), -34.8, -55.998)));
        assertEquals(2, caso(creado.getId()).getClavesEventosTracking().size());
        assertEquals(tarde, circulacion(creado.getId()).getFechaInicio());
    }

    @Test
    void gpsInmovilNoAbreExpediente() {
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -34.90001, -56.1))));
        assertEquals(List.of(), enTx(() -> servicio.casosAbiertos("SBC1234")));
        assertThrows(IllegalArgumentException.class, () -> enTx(() ->
                servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1)));
    }

    @ParameterizedTest(name = "TR-C01: reentrega de GPS desde {0}")
    @EnumSource(value = EstadoCaso.class, names = { "EN_DESCARGO", "CONFIRMADA", "DESESTIMADA" })
    void reprocesarGpsConservaSuCasoInclusoSiEstaResuelto(EstadoCaso estado) {
        DatosCaso primero = iniciarCirculacion();
        enTx(() -> servicio.notificarCaso(primero.getId(), "notificacion"));
        enTx(() -> {
            CasoFiscalizacion c = em.find(CasoFiscalizacion.class, primero.getId());
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            if (estado.esFinal()) c.cambiarEstado(estado);
            return null;
        });
        var claves = List.copyOf(caso(primero.getId()).getClavesEventosTracking());
        var registros = auditoria(primero.getId()).stream().map(RegistroAuditoriaCaso::getId).toList();
        DatosCaso repetido = enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1)));
        assertEquals(primero.getId(), repetido.getId());
        assertEquals(estado.name(), repetido.getEstado());
        assertEquals(claves, caso(primero.getId()).getClavesEventosTracking());
        assertEquals(registros, auditoria(primero.getId()).stream().map(RegistroAuditoriaCaso::getId).toList());
        assertEquals(1L, em.createQuery("SELECT COUNT(c) FROM CasoFiscalizacion c", Long.class).getSingleResult());
    }

    @ParameterizedTest(name = "TR-C02: GPS nuevo después de {0}")
    @EnumSource(value = EstadoCaso.class, names = { "CONFIRMADA", "DESESTIMADA" })
    void gpsNuevoTrasResolucionCreaOtroCasoYLaReentregaConservaElAnterior(EstadoCaso estado) {
        DatosCaso anterior = iniciarCirculacion();
        enTx(() -> servicio.notificarCaso(anterior.getId(), "notificacion"));
        enTx(() -> {
            var c = em.find(CasoFiscalizacion.class, anterior.getId());
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            c.cambiarEstado(estado);
            return null;
        });
        var evidenciaAnterior = List.copyOf(caso(anterior.getId()).getClavesEventosTracking());
        var auditoriaAnterior = auditoria(anterior.getId()).stream().map(RegistroAuditoriaCaso::getId).toList();
        Instant siguiente = T2.plusSeconds(10);
        var nuevo = enTx(() -> servicio.registrarCirculacionSinGuia(gps(siguiente, -34.9, -56.096)));
        assertNotEquals(anterior.getId(), nuevo.getId());
        assertEquals("DETECTADA", nuevo.getEstado());
        assertEquals(List.of(new ClaveEvento("SBC1234", siguiente)), caso(nuevo.getId()).getClavesEventosTracking());
        assertEquals(MotivoFinCirculacionSinGuia.CASO_RESUELTO, circulacion(anterior.getId()).getMotivoFin());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(nuevo.getId()).getEstado());

        var repetido = enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -34.9, -56.098)));
        assertEquals(anterior.getId(), repetido.getId());
        assertEquals(estado, caso(anterior.getId()).getEstado());
        assertEquals(evidenciaAnterior, caso(anterior.getId()).getClavesEventosTracking());
        assertEquals(auditoriaAnterior, auditoria(anterior.getId()).stream().map(RegistroAuditoriaCaso::getId).toList());
        assertEquals(2L, em.createQuery("SELECT COUNT(c) FROM CasoFiscalizacion c", Long.class).getSingleResult());
    }

    @Test
    void nuevosViajesSinGuiaSeAgreganAlMismoCasoAunqueEsteEnDescargo() {
        DatosCaso primero = iniciarCirculacion();
        enTx(() -> {
            CasoFiscalizacion c = em.find(CasoFiscalizacion.class, primero.getId());
            c.cambiarEstado(EstadoCaso.NOTIFICADA);
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            return null;
        });
        DatosCaso siguiente = enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(T1.plusSeconds(86400), -34.8, -56.0)));
        assertEquals(primero.getId(), siguiente.getId());
        assertEquals("EN_DESCARGO", siguiente.getEstado());
        assertEquals(3, caso(primero.getId()).getClavesEventosTracking().size());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(primero.getId()).getEstado());
    }

    @Test
    void unPesajePuedeSerEvidenciaDeDosCasosYNoSeRepite() {
        DatosCaso exceso = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));
        DatosCaso habilitacion = enTx(() -> servicio.registrarHabilitacion("GV-1", PESAJE_T1, sinDocumentos(T1)));
        enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));

        assertEquals(1, caso(exceso.getId()).getEventosPesajes().size());
        assertEquals(1, caso(habilitacion.getId()).getEventosPesajes().size());
    }

    @Test
    void guiasDistintasDelMismoVehiculoCreanCasosIndependientes() {
        Guia segunda = new Guia("GV-2", "Agropecuario", 20_000, LocalDate.of(2026, 10, 18),
                "Mercedes", "Nueva Palmira", em.find(Empresa.class, 1L));
        segunda.setVehiculo(em.find(Vehiculo.class, "SBC1234"));
        enTx(() -> { em.persist(segunda); return null; });

        DatosCaso primero = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, prueba(T1)));
        DatosCaso otro = enTx(() -> servicio.registrarDesvio("GV-2", TRACKING_T2, prueba(T2)));
        DatosCaso reuso = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T2, prueba(T2)));

        assertEquals(primero.getId(), reuso.getId());
        assertNotEquals(primero.getId(), otro.getId());
        assertEquals(2, caso(primero.getId()).getClavesEventosTracking().size());
        assertEquals(1, caso(otro.getId()).getClavesEventosTracking().size());
        assertEquals(2, caso(primero.getId()).getEvidenciasDesvio().size());
        assertEquals(1, caso(otro.getId()).getEvidenciasDesvio().size());
    }

    @Test
    void desvioReentregadoConservaUnaSolaPruebaYGps() {
        DatosCaso a = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, prueba(T1)));
        DatosCaso b = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, prueba(T1)));
        assertEquals(a.getId(), b.getId());
        var c = caso(a.getId());
        assertEquals(1, c.getClavesEventosTracking().size());
        assertEquals(1, c.getEvidenciasDesvio().size());
        var guardada = c.getEvidenciasDesvio().get(0);
        assertEquals(T1.plusSeconds(10), guardada.getFechaPareja());
        assertEquals(2600.0, guardada.getDistanciaPareja());
        assertEquals("sha-ruta-a,sha-ruta-b", guardada.getRutasSha());
    }

    @Test
    void desvioInvalidoNoDejaCasoParcial() {
        PruebaDesvioRemota invalida = new PruebaDesvioRemota(T1.plusSeconds(31), -34.9, -56.1,
                -34.9, -56.12, 2400, 2600, 2000, 30, List.of("sha-a"));
        assertThrows(IllegalArgumentException.class,
                () -> enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, invalida)));
        assertEquals(List.of(), enTx(() -> servicio.casosAbiertos("SBC1234")));
        assertThrows(IllegalArgumentException.class,
                () -> enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.DESVIO_RUTA, "GV-1", TRACKING_T1)));
    }

    @Test
    void casoSinGuiaNoSeVinculaAUnaGuiaInventada() {
        DatosCaso a = iniciarCirculacion();
        assertNull(caso(a.getId()).getGuia());
        assertEquals(2, caso(a.getId()).getClavesEventosTracking().size());
    }

    @Test
    void detencionProlongadaYReanudacionConservanElMismoCaso() {
        DatosCaso creado = iniciarCirculacion();
        Instant detencion = T2.plusSeconds(30 * 60);
        assertEquals(creado.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(detencion, -34.9, -56.098))).getId());
        assertEquals(creado.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(detencion.plusSeconds(10), -34.9, -56.096))).getId());
        CirculacionSinGuia actual = circulacion(creado.getId());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, actual.getEstado());
        assertNull(actual.getMotivoFin());
        assertNull(actual.getFechaFin());
        assertEquals(4, caso(creado.getId()).getClavesEventosTracking().size());
        assertEquals(EstadoCaso.DETECTADA, caso(creado.getId()).getEstado());
    }

    @Test
    void cierrePorGuiaNoConfundeEstadoAdministrativo() {
        DatosCaso creado = iniciarCirculacion();
        enTx(() -> { servicio.cerrarCirculacionPorGuia("SBC1234", T2.plusSeconds(5)); return null; });
        assertEquals(EstadoCirculacionSinGuia.FINALIZADA, circulacion(creado.getId()).getEstado());
        assertEquals(MotivoFinCirculacionSinGuia.GUIA_INICIADA,
                circulacion(creado.getId()).getMotivoFin());
        assertEquals(EstadoCaso.DETECTADA, caso(creado.getId()).getEstado());
    }

    @Test
    void circularDeNuevoSinGuiaTrasUnPeriodoConGuiaIniciaOtroCaso() {
        DatosCaso primero = iniciarCirculacion();
        enTx(() -> { servicio.cerrarCirculacionPorGuia("SBC1234", T2.plusSeconds(5)); return null; });
        Instant siguiente = T1.plusSeconds(86400);
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(siguiente, -34.9, -56.1))));
        DatosCaso nuevo = enTx(() -> servicio.registrarCirculacionSinGuia(
                gps(siguiente.plusSeconds(10), -34.9, -56.098)));
        assertNotEquals(primero.getId(), nuevo.getId());
        assertEquals(MotivoFinCirculacionSinGuia.GUIA_INICIADA, circulacion(primero.getId()).getMotivoFin());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(nuevo.getId()).getEstado());
    }

    @Test
    void elCasoLlevaLaDescripcionDeSuTipo() {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));

        assertEquals(TipoCasoAutomatico.EXCESO_CARGA.getDescripcion(), caso(creado.getId()).getDescripcion());
    }

    @Test
    void noCreaUnCasoConUnaGuiaInexistenteNiSinTipo() {
        assertThrows(IllegalArgumentException.class,
                () -> enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-NO-EXISTE", PESAJE_T1)));
        assertThrows(IllegalArgumentException.class, () -> enTx(() -> servicio.registrarEvidencia(null, null, TRACKING_T1)));

        assertEquals(List.of(), enTx(() -> servicio.casosAbiertos("SBC1234")));
    }

    private void rechaza(Supplier<?> accion) {
        assertThrows(IllegalArgumentException.class, () -> enTx(accion));
    }

    private List<CirculacionSinGuia> circulaciones() {
        em.clear();
        return em.createQuery("SELECT c FROM CirculacionSinGuia c ORDER BY c.id", CirculacionSinGuia.class)
                .getResultList();
    }

    @Test
    void aTripWithoutGuideDoesNotUseTheGenericOperation() {
        rechaza(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
    }

    @Test
    void anEventNeedsAllItsData() {
        TipoCasoAutomatico tipo = TipoCasoAutomatico.VEHICULO_SIN_HABILITACION;

        rechaza(() -> servicio.registrarEvidencia(tipo, null, null));
        rechaza(() -> servicio.registrarEvidencia(tipo, null, new EventoFiscalizado(null, T1, OrigenEvento.TRACKING)));
        rechaza(() -> servicio.registrarEvidencia(tipo, null, new EventoFiscalizado("SBC1234", null, OrigenEvento.TRACKING)));
        rechaza(() -> servicio.registrarEvidencia(tipo, null, new EventoFiscalizado("SBC1234", T1, null)));
    }

    @Test
    void anUnknownVehicleOpensNoCase() {
        rechaza(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, null,
                new EventoFiscalizado("NOEXISTE", T1, OrigenEvento.TRACKING)));
    }

    @Test
    void aGuideOfAnotherVehicleIsRejected() {
        enTx(() -> {
            em.persist(new Vehiculo("XYZ1111", "Volvo", "FH", 8500, 25000, em.find(Empresa.class, 1L)));
            return null;
        });

        rechaza(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, "GV-1",
                new EventoFiscalizado("XYZ1111", T1, OrigenEvento.TRACKING)));
    }

    @Test
    void aWeighingThatIsNotStoredIsNotEvidence() {
        rechaza(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-1",
                new EventoFiscalizado("SBC1234", T2, OrigenEvento.PESAJE)));
    }

    @Test
    void aNewDeviationAddsItsProofToTheOpenCase() {
        DatosCaso primero = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, prueba(T1)));
        DatosCaso segundo = enTx(() -> servicio.registrarDesvio("GV-1", TRACKING_T2, prueba(T2)));

        assertEquals(primero.getId(), segundo.getId());
        assertEquals(2, caso(primero.getId()).getEvidenciasDesvio().size());
        assertEquals(2, caso(primero.getId()).getClavesEventosTracking().size());
    }

    private static PruebaDesvioRemota pruebaCon(Instant pareja, double distancia, double distanciaPareja, double umbral,
            long ventana, double latitud, List<String> hashes) {
        return new PruebaDesvioRemota(pareja, latitud, -56.1, latitud, -56.12, distancia, distanciaPareja, umbral,
                ventana, hashes);
    }

    private void pruebaInvalida(PruebaDesvioRemota invalida) {
        rechaza(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, invalida));
    }

    @Test
    void aDeviationNeedsItsGuideGpsAndProof() {
        rechaza(() -> servicio.registrarDesvio(null, TRACKING_T1, prueba(T1)));
        rechaza(() -> servicio.registrarDesvio(" ", TRACKING_T1, prueba(T1)));
        rechaza(() -> servicio.registrarDesvio("GV-1", null, prueba(T1)));
        rechaza(() -> servicio.registrarDesvio("GV-1", PESAJE_T1, prueba(T1)));
        rechaza(() -> servicio.registrarDesvio("GV-1", TRACKING_T1, null));
    }

    @Test
    void anIncompleteProofIsRejected() {
        Instant pareja = T1.plusSeconds(10);
        List<String> hash = List.of("sha");

        pruebaInvalida(pruebaCon(null, 2400, 2600, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, null));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, List.of()));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, List.of("a", "b", "c", "d")));
    }

    @Test
    void aProofWithWrongValuesIsRejected() {
        Instant pareja = T1.plusSeconds(10);
        List<String> hash = List.of("sha");

        pruebaInvalida(pruebaCon(T1, 2400, 2600, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 0, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, Double.NaN, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 0, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, Double.NaN, 2600, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, Double.NaN, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2000, 2600, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2000, 2000, 30, -34.9, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, 91, hash));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, List.of(" ")));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, List.of("a,b")));
        pruebaInvalida(pruebaCon(pareja, 2400, 2600, 2000, 30, -34.9, List.of("a".repeat(65))));
    }

    @Test
    void anInvalidPositionIsRejected() {
        rechaza(() -> servicio.registrarCirculacionSinGuia(null));
        rechaza(() -> servicio.registrarCirculacionSinGuia(new PosicionSinGuiaRemota(null, T1, -34.9, -56.1)));
        rechaza(() -> servicio.registrarCirculacionSinGuia(new PosicionSinGuiaRemota(" ", T1, -34.9, -56.1)));
        rechaza(() -> servicio.registrarCirculacionSinGuia(gps(null, -34.9, -56.1)));
        rechaza(() -> servicio.registrarCirculacionSinGuia(gps(T1, Double.NaN, -56.1)));
        rechaza(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, 181)));
        rechaza(() -> servicio.registrarCirculacionSinGuia(new PosicionSinGuiaRemota("NOEXISTE", T1, -34.9, -56.1)));
    }

    @Test
    void aRedeliveredPositionKeepsItsCase() {
        DatosCaso creado = iniciarCirculacion();

        assertEquals(creado.getId(), enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -34.9, -56.098))).getId());
        assertEquals(2, caso(creado.getId()).getClavesEventosTracking().size());
    }

    @Test
    void theSamePendingPositionChangesNothing() {
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));

        assertEquals(1, circulaciones().size());
    }

    @Test
    void aDelayedPositionJoinsItsCirculation() {
        DatosCaso creado = iniciarCirculacion();

        DatosCaso asociado = enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1.plusSeconds(5), -34.9, -56.099)));

        assertEquals(creado.getId(), asociado.getId());
        assertEquals(3, caso(creado.getId()).getClavesEventosTracking().size());
    }

    @Test
    void aPositionBeforeTheCirculationNeedsReconciliation() {
        iniciarCirculacion();

        rechaza(() -> servicio.registrarCirculacionSinGuia(gps(T1.minusSeconds(100), -34.9, -56.1)));
    }

    @Test
    void aPositionBeforeAFinishedEpisodeNeedsReconciliation() {
        iniciarCirculacion();
        enTx(() -> {
            servicio.cerrarCirculacionPorGuia("SBC1234", T2.plusSeconds(5));
            return null;
        });

        rechaza(() -> servicio.registrarCirculacionSinGuia(gps(T1.minusSeconds(100), -34.9, -56.1)));
    }

    @Test
    void aLongGapKeepsTheActiveCirculationAndItsCase() {
        DatosCaso creado = iniciarCirculacion();
        Instant tarde = T2.plusSeconds(46 * 60);

        DatosCaso asociado = enTx(() -> servicio.registrarCirculacionSinGuia(gps(tarde, -34.9, -56.09)));

        assertEquals(creado.getId(), asociado.getId());
        assertEquals(1, circulaciones().size());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(creado.getId()).getEstado());
        assertNull(circulacion(creado.getId()).getMotivoFin());
        assertNull(circulacion(creado.getId()).getFechaFin());
        assertEquals(3, caso(creado.getId()).getClavesEventosTracking().size());
    }

    @Test
    void aMovementAfterTheStartWindowRestartsThePendingOne() {
        Instant tarde = T1.plusSeconds(200);
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(tarde, -34.9, -56.09))));

        assertEquals(tarde, circulaciones().get(0).getFechaInicio());
    }

    @Test
    void anImpossibleSpeedConfirmsNoMovement() {
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T1, -34.9, -56.1))));
        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2, -30.0, -56.1))));

        assertEquals(EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO, circulaciones().get(0).getEstado());
    }

    @Test
    void anImpossibleJumpIsNotPartOfTheTrajectory() {
        DatosCaso creado = iniciarCirculacion();

        assertNull(enTx(() -> servicio.registrarCirculacionSinGuia(gps(T2.plusSeconds(10), -30.0, -56.1))));

        assertEquals(2, caso(creado.getId()).getClavesEventosTracking().size());
    }

    @Test
    void anActiveCirculationAddsItsPositions() {
        DatosCaso creado = iniciarCirculacion();
        Instant movimiento = T2.plusSeconds(60);
        Instant quieto = T2.plusSeconds(70);

        enTx(() -> servicio.registrarCirculacionSinGuia(gps(movimiento, -34.9, -56.09)));
        enTx(() -> servicio.registrarCirculacionSinGuia(gps(quieto, -34.9, -56.09)));

        assertEquals(4, caso(creado.getId()).getClavesEventosTracking().size());
        assertEquals(movimiento, circulacion(creado.getId()).getUltimoMovimiento());
        assertEquals(quieto, circulacion(creado.getId()).getUltimaMedicion());
    }

    @Test
    void closingByGuideNeedsItsContext() {
        rechaza(() -> {
            servicio.cerrarCirculacionPorGuia(null, T1);
            return null;
        });
        rechaza(() -> {
            servicio.cerrarCirculacionPorGuia("SBC1234", null);
            return null;
        });
    }

    @Test
    void closingByGuideWithoutCirculationDoesNothing() {
        enTx(() -> {
            servicio.cerrarCirculacionPorGuia("NOEXISTE", T1);
            servicio.cerrarCirculacionPorGuia("SBC1234", T1);
            return null;
        });

        assertEquals(List.of(), circulaciones());
    }

    @Test
    void aGuideOverlappingTheCirculationNeedsReconciliation() {
        iniciarCirculacion();

        assertThrows(IllegalStateException.class, () -> enTx(() -> {
            servicio.cerrarCirculacionPorGuia("SBC1234", T1);
            return null;
        }));
    }

    @Test
    void resumingAfterDaysWithoutSignalKeepsTheCase() {
        DatosCaso creado = iniciarCirculacion();
        Instant vieja = Instant.now().minusSeconds(3 * 24 * 60 * 60);
        enTx(() -> em.createQuery("UPDATE CirculacionSinGuia c SET c.ultimaRecepcion = :fecha")
                .setParameter("fecha", vieja).executeUpdate());
        em.clear();
        Instant regreso = T2.plusSeconds(3 * 24 * 60 * 60);

        DatosCaso asociado = enTx(() -> servicio.registrarCirculacionSinGuia(gps(regreso, -34.9, -56.09)));

        assertEquals(creado.getId(), asociado.getId());
        assertEquals(1, circulaciones().size());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, circulacion(creado.getId()).getEstado());
        assertNull(circulacion(creado.getId()).getFechaFin());
        assertNull(circulacion(creado.getId()).getMotivoFin());
        assertEquals(3, caso(creado.getId()).getClavesEventosTracking().size());
    }

    @Test
    void anUnknownCaseCannotBeNotified() {
        rechaza(() -> servicio.notificarCaso(999L, "c"));
    }

    private <T> T enTx(Supplier<T> accion) {
        em.getTransaction().begin();
        try {
            T r = accion.get();
            em.getTransaction().commit();
            return r;
        } catch (RuntimeException e) {
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
