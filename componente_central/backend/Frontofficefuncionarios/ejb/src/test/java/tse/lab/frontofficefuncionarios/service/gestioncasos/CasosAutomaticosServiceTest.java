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

import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
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

// Operaciones de la fiscalizacion automatica sobre los casos (EJB remoto) contra H2 con los DAOs reales.
class CasosAutomaticosServiceTest {

    private static final Instant T1 = Instant.parse("2026-10-17T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-17T10:00:10Z");
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
        set(servicio, "auditoria", conEm(new AuditoriaCasoDAO()));

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

    private CasoFiscalizacion caso(long id) {
        em.clear();
        return em.find(CasoFiscalizacion.class, id);
    }

    private List<RegistroAuditoriaCaso> auditoria(long idCaso) {
        return em.createQuery("SELECT r FROM RegistroAuditoriaCaso r WHERE r.idCaso = :id ORDER BY r.id",
                RegistroAuditoriaCaso.class).setParameter("id", idCaso).getResultList();
    }

    @Test
    void creaElCasoDetectadoConElVehiculoYElEventoComoEvidencia() {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));

        assertEquals("DETECTADA", creado.getEstado());
        CasoFiscalizacion c = caso(creado.getId());
        assertEquals("VIAJE_SIN_GUIA", c.getTipoCaso());
        assertEquals("SBC1234", c.getVehiculo().getMatricula());
        assertNull(c.getGuia());
        assertEquals(List.of(new ClaveEvento("SBC1234", T1)), c.getClavesEventosTracking());
        RegistroAuditoriaCaso creacion = auditoria(creado.getId()).get(0);
        assertEquals(RegistroAuditoriaCaso.SISTEMA, creacion.getUsuario());
        assertEquals(AccionAuditoriaCaso.CREACION, creacion.getAccion());
        assertEquals(EstadoCaso.DETECTADA, creacion.getEstadoNuevo());
    }

    @Test
    void unCasoConGuiaQuedaAsociadoAEllaYAlPesaje() {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));

        CasoFiscalizacion c = caso(creado.getId());
        assertEquals("GV-1", c.getGuia().getId());
        assertEquals(1, c.getEventosPesajes().size());
    }

    @Test
    void notificarPasaElCasoANotificadaYQuedaEnLaAuditoriaComoSistema() {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));

        DatosCaso notificado = enTx(() -> servicio.notificarCaso(creado.getId()));

        assertEquals("NOTIFICADA", notificado.getEstado());
        RegistroAuditoriaCaso cambio = auditoria(creado.getId()).get(1);
        assertEquals(RegistroAuditoriaCaso.SISTEMA, cambio.getUsuario());
        assertEquals(AccionAuditoriaCaso.CAMBIO_ESTADO, cambio.getAccion());
        assertEquals(EstadoCaso.DETECTADA, cambio.getEstadoAnterior());
        assertEquals(EstadoCaso.NOTIFICADA, cambio.getEstadoNuevo());
    }

    @Test
    void notificarUnCasoQueYaNoEstaDetectadoNoHaceNada() {
        DatosCaso creado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        enTx(() -> servicio.notificarCaso(creado.getId()));

        DatosCaso otraVez = enTx(() -> servicio.notificarCaso(creado.getId()));

        assertEquals("NOTIFICADA", otraVez.getEstado());
        assertEquals(2, auditoria(creado.getId()).size());
    }

    @Test
    void losCasosAbiertosSonLosDetectadosYNotificados() {
        DatosCaso detectado = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        DatosCaso enDescargo = enTx(() -> servicio.registrarEvidencia(TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, null, TRACKING_T2));
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
        DatosCaso sinGuia = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        DatosCaso habilitacion = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, null, TRACKING_T1));
        DatosCaso repetido = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));

        assertEquals(sinGuia.getId(), repetido.getId());
        assertEquals(1, caso(sinGuia.getId()).getClavesEventosTracking().size());
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
    void reprocesarUnEventoDeCasoEnDescargoNoAbreUnSegundoCaso() {
        DatosCaso primero = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        enTx(() -> {
            CasoFiscalizacion c = em.find(CasoFiscalizacion.class, primero.getId());
            c.cambiarEstado(EstadoCaso.NOTIFICADA);
            c.cambiarEstado(EstadoCaso.EN_DESCARGO);
            return null;
        });
        DatosCaso repetido = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        assertEquals(primero.getId(), repetido.getId());
        DatosCaso segundo = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T2));
        assertNotEquals(primero.getId(), segundo.getId());
        assertEquals(1, caso(primero.getId()).getClavesEventosTracking().size());
        assertEquals(1, caso(segundo.getId()).getClavesEventosTracking().size());
    }

    @Test
    void unPesajePuedeSerEvidenciaDeDosCasosYNoSeRepite() {
        DatosCaso exceso = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.EXCESO_CARGA, "GV-1", PESAJE_T1));
        DatosCaso habilitacion = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, "GV-1", PESAJE_T1));
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

        DatosCaso primero = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.DESVIO_RUTA, "GV-1", TRACKING_T1));
        DatosCaso otro = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.DESVIO_RUTA, "GV-2", TRACKING_T2));
        DatosCaso reuso = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.DESVIO_RUTA, "GV-1", TRACKING_T2));

        assertEquals(primero.getId(), reuso.getId());
        assertNotEquals(primero.getId(), otro.getId());
        assertEquals(2, caso(primero.getId()).getClavesEventosTracking().size());
        assertEquals(1, caso(otro.getId()).getClavesEventosTracking().size());
    }

    @Test
    void casoSinGuiaNoSeVinculaAUnaGuiaInventada() {
        DatosCaso a = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T1));
        DatosCaso b = enTx(() -> servicio.registrarEvidencia(
                TipoCasoAutomatico.VIAJE_SIN_GUIA, null, TRACKING_T2));
        assertEquals(a.getId(), b.getId());
        assertNull(caso(a.getId()).getGuia());
        assertEquals(2, caso(a.getId()).getClavesEventosTracking().size());
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
