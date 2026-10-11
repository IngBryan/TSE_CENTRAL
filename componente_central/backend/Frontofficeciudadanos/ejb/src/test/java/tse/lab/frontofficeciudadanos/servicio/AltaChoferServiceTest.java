package tse.lab.frontofficeciudadanos.servicio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.PerfilCiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.RegistroConsultaPersonaDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.model.Chofer;
import tse.lab.frontofficeciudadanos.model.Empresa;
import tse.lab.frontofficeciudadanos.model.EstadoChofer;
import tse.lab.frontofficeciudadanos.model.EstadoValidacion;
import tse.lab.frontofficeciudadanos.model.PerfilCiudadano;
import tse.lab.frontofficeciudadanos.model.Responsable;
import tse.lab.frontofficeciudadanos.pdi.ClientePdi;
import tse.lab.frontofficeciudadanos.pdi.DatosPersonaPdi;
import tse.lab.frontofficeciudadanos.pdi.PdiNoDisponibleException;
import tse.lab.frontofficeciudadanos.pdi.ValidacionPdiService;
import tse.lab.frontofficeciudadanos.servicio.ResultadoAltaChofer.Resultado;

// CU-02 contra H2 con los DAOs reales y una PDI simulada.
class AltaChoferServiceTest {

    private static final String RESPONSABLE = "54074380";

    // PDI simulada: cedula -> persona; ausente = no existe; "caida" = no responde
    static class PdiFalsa extends ClientePdi {
        final Map<String, DatosPersonaPdi> personas = new HashMap<>();
        boolean caida;
        int consultas;

        @Override
        public Optional<DatosPersonaPdi> consultarPersona(String cedula, String correlationId)
                throws PdiNoDisponibleException {
            consultas++;
            if (caida) {
                throw new PdiNoDisponibleException("caida");
            }
            return Optional.ofNullable(personas.get(cedula));
        }
    }

    private EntityManagerFactory emf;
    private EntityManager em;
    private PdiFalsa pdi;
    private AltaChoferService servicio;
    private ValidacionPdiService validacion;
    private Empresa empresa;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        pdi = new PdiFalsa();
        pdi.personas.put("56087406", persona("Juan", "Pérez", LocalDate.of(1990, 5, 10)));
        pdi.personas.put("33333333", persona("Lucía", "Fernández", LocalDate.now().minusYears(16)));

        ResponsableDAO responsables = inyectarEm(new ResponsableDAO());
        PerfilCiudadanoDAO perfiles = inyectarEm(new PerfilCiudadanoDAO());
        ChoferDAO choferes = inyectarEm(new ChoferDAO());
        RegistroConsultaPersonaDAO auditoria = inyectarEm(new RegistroConsultaPersonaDAO());
        validacion = new ValidacionPdiService();
        set(validacion, "clientePdi", pdi);
        set(validacion, "perfiles", perfiles);
        set(validacion, "choferes", choferes);
        servicio = new AltaChoferService();
        set(servicio, "responsables", responsables);
        set(servicio, "perfiles", perfiles);
        set(servicio, "choferes", choferes);
        set(servicio, "auditoria", auditoria);
        set(servicio, "validacionPdi", validacion);

        empresa = new Empresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Pando", null);
        empresa.setHabilitada(true);
        PerfilCiudadano perfilResponsable = new PerfilCiudadano(RESPONSABLE, "r@prueba.uy", "sub-r");
        perfilResponsable.setEstado(EstadoValidacion.VALIDADO);
        enTransaccion(() -> {
            em.persist(empresa);
            em.persist(new Responsable(RESPONSABLE, "r@prueba.uy", empresa));
            em.persist(perfilResponsable);
        });
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void altaCompletaConsultaMuestraNombreYConfirmarHabilita() throws Exception {
        ResultadoAltaChofer consulta = enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        assertEquals(Resultado.CONFIRMAR, consulta.resultado());
        assertEquals("Juan", consulta.primerNombre());
        assertEquals("Pérez", consulta.primerApellido());

        ResultadoAltaChofer alta = enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        assertEquals(Resultado.ALTA_CONFIRMADA, alta.resultado());
        assertEquals("HABILITADO", alta.estado());
        assertEquals(EstadoChofer.HABILITADO, chofer("56087406").getEstado());
        // Ley 18.331: consulta y confirmacion quedan registradas
        assertEquals(2, inyectarEm(new RegistroConsultaPersonaDAO()).buscarPorCiConsultada("56087406").size());
    }

    @Test
    void fa1YaEsChoferDeLaEmpresa() throws Exception {
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        assertEquals(Resultado.YA_ES_CHOFER,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t")).resultado());
        assertEquals(Resultado.YA_ES_CHOFER,
                enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t")).resultado());
    }

    @Test
    void fa2PersonaValidadaNoConsultaLaPdi() throws Exception {
        PerfilCiudadano validada = new PerfilCiudadano("22222222", "m@prueba.uy", "sub-m");
        validada.setEstado(EstadoValidacion.VALIDADO);
        validada.setPrimerNombre("María");
        validada.setPrimerApellido("Rodríguez");
        enTransaccion(() -> em.persist(validada));

        ResultadoAltaChofer r = enTransaccion(() -> servicio.consultar(RESPONSABLE, "22222222", "t"));
        assertEquals(Resultado.CONFIRMAR, r.resultado());
        assertEquals("María", r.primerNombre());
        assertEquals(0, pdi.consultas);
    }

    @Test
    void fa3PersonaPendienteSeValida() throws Exception {
        enTransaccion(() -> em.persist(new PerfilCiudadano("56087406", null, null)));
        assertEquals(Resultado.CONFIRMAR,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t")).resultado());
        assertEquals(1, pdi.consultas);
    }

    @Test
    void fa4NoExisteOMenorNoAsocia() throws Exception {
        assertEquals(Resultado.NO_EXISTE,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "99999999", "t")).resultado());
        assertEquals(Resultado.MENOR_DE_EDAD,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "33333333", "t")).resultado());
        assertNull(chofer("99999999"));
        assertNull(chofer("33333333"));
        // y no se puede confirmar salteando la consulta
        assertEquals(Resultado.DEBE_CONSULTAR,
                enTransaccion(() -> servicio.confirmar(RESPONSABLE, "33333333", "t")).resultado());
    }

    @Test
    void fa5PdiCaidaAsociaPendienteYSeHabilitaAlValidar() throws Exception {
        pdi.caida = true;
        ResultadoAltaChofer r = enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        assertEquals(Resultado.ASOCIADO_PENDIENTE, r.resultado());
        assertNull(r.primerNombre());
        assertEquals(EstadoChofer.PENDIENTE_VALIDACION, chofer("56087406").getEstado());

        // CU-04 (o un ingreso de la persona) la valida: la asociacion queda habilitada
        pdi.caida = false;
        enTransaccion(() -> validacion.validar(em.find(PerfilCiudadano.class, "56087406"), "t"));
        em.clear();
        assertEquals(EstadoChofer.HABILITADO, chofer("56087406").getEstado());
    }

    @Test
    void unChoferPuedeEstarEnVariasEmpresas() throws Exception {
        Empresa otra = new Empresa(2L, "Otra", "Otra S.A.", "Montevideo", null);
        otra.setHabilitada(true);
        enTransaccion(() -> {
            em.persist(otra);
            em.persist(new Chofer("56087406", null, otra));
        });
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        assertEquals(Resultado.ALTA_CONFIRMADA,
                enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t")).resultado());
        assertEquals(2, inyectarEm(new ChoferDAO()).buscarPorCi("56087406").size());
    }

    @Test
    void soloElResponsableValidadoDeUnaEmpresaHabilitada() throws Exception {
        assertThrows(NoAutorizadoException.class, () -> servicio.consultar("11111111", "56087406", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.listar(null, "t"));

        enTransaccion(() -> em.find(PerfilCiudadano.class, RESPONSABLE).setEstado(EstadoValidacion.PENDIENTE_VALIDACION));
        assertThrows(NoAutorizadoException.class, () -> servicio.consultar(RESPONSABLE, "56087406", "t"));

        enTransaccion(() -> {
            em.find(PerfilCiudadano.class, RESPONSABLE).setEstado(EstadoValidacion.VALIDADO);
            em.find(Empresa.class, 1L).setHabilitada(false);
        });
        assertThrows(NoAutorizadoException.class, () -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        assertEquals(0, pdi.consultas);
    }

    @Test
    void listarDevuelveSoloPrimerNombreYApellido() throws Exception {
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        List<AltaChoferService.ChoferVista> choferes = servicio.listar(RESPONSABLE, "t");
        assertEquals(List.of(new AltaChoferService.ChoferVista("56087406", "Juan", "Pérez", "HABILITADO")), choferes);
        assertTrue(AltaChoferService.ChoferVista.class.getRecordComponents().length == 4); // sin fecha de nacimiento
    }

    // ===== helpers =====

    private static DatosPersonaPdi persona(String nombre, String apellido, LocalDate nacimiento) {
        return new DatosPersonaPdi(nombre, apellido, nombre, apellido, nacimiento);
    }

    private Chofer chofer(String ci) {
        return inyectarEm(new ChoferDAO()).buscarPorCiYEmpresa(ci, em.find(Empresa.class, 1L));
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    interface AccionVoid {
        void ejecutar() throws Exception;
    }

    private <T> T enTransaccion(Accion<T> accion) throws Exception {
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

    private void enTransaccion(AccionVoid accion) throws Exception {
        enTransaccion(() -> {
            accion.ejecutar();
            return null;
        });
    }

    private <T> T inyectarEm(T dao) {
        try {
            set(dao, "em", em);
            return dao;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
