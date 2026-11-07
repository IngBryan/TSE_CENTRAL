package tse.lab.frontofficeciudadanos.service.gestionchoferes;

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
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.RegistroConsultaPersonaDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.adaptadorpdi.ClientePdi;
import tse.lab.adaptadorpdi.DatosPersonaPdi;
import tse.lab.adaptadorpdi.PdiNoDisponibleException;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ValidacionPdiService;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.autenticacion.RolesService;
import tse.lab.frontofficeciudadanos.service.gestionchoferes.ResultadoAltaChofer.Resultado;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.model.Chofer;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;
import tse.lab.model.EstadoGuia;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Ciudadano;
import tse.lab.model.Guia;
import tse.lab.model.Responsable;
import tse.lab.model.Vehiculo;

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
        CiudadanoDAO perfiles = inyectarEm(new CiudadanoDAO());
        ChoferDAO choferes = inyectarEm(new ChoferDAO());
        RegistroConsultaPersonaDAO auditoria = inyectarEm(new RegistroConsultaPersonaDAO());
        validacion = new ValidacionPdiService();
        set(validacion, "clientePdi", pdi);
        set(validacion, "perfiles", perfiles);
        set(validacion, "choferes", choferes);
        servicio = new AltaChoferService();
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", responsables);
        set(autorizacion, "perfiles", perfiles);
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "perfiles", perfiles);
        set(servicio, "choferes", choferes);
        set(servicio, "auditoria", auditoria);
        set(servicio, "validacionPdi", validacion);
        set(servicio, "guias", inyectarEm(new GuiaDAO()));

        empresa = new Empresa(1L, "Transportes del Este", "Transportes del Este S.A.", "Pando", null);
        empresa.setHabilitada(true);
        Ciudadano perfilResponsable = new Ciudadano(RESPONSABLE, "r@prueba.uy", "sub-r");
        perfilResponsable.setEstado(EstadoValidacion.VALIDADO);
        enTransaccion(() -> {
            em.persist(empresa);
            em.persist(perfilResponsable);
            em.persist(new Responsable(perfilResponsable, empresa));
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
        Ciudadano validada = new Ciudadano("22222222", "m@prueba.uy", "sub-m");
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
        enTransaccion(() -> em.persist(new Ciudadano("56087406", null, null)));
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
        enTransaccion(() -> validacion.validar(em.find(Ciudadano.class, "56087406"), "t"));
        em.clear();
        assertEquals(EstadoChofer.HABILITADO, chofer("56087406").getEstado());
    }

    @Test
    void unChoferPuedeEstarEnVariasEmpresas() throws Exception {
        Empresa otra = new Empresa(2L, "Otra", "Otra S.A.", "Montevideo", null);
        otra.setHabilitada(true);
        enTransaccion(() -> {
            em.persist(otra);
            Ciudadano persona = new Ciudadano("56087406", null, null);
            em.persist(persona);
            em.persist(new Chofer(persona, otra));
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

        enTransaccion(() -> em.find(Ciudadano.class, RESPONSABLE).setEstado(EstadoValidacion.PENDIENTE_VALIDACION));
        assertThrows(NoAutorizadoException.class, () -> servicio.consultar(RESPONSABLE, "56087406", "t"));

        enTransaccion(() -> {
            em.find(Ciudadano.class, RESPONSABLE).setEstado(EstadoValidacion.VALIDADO);
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

    @Test
    void darDeBajaLoQuitaDelListadoYSePuedeVolverAAgregar() throws Exception {
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        Long id = chofer("56087406").getId();

        enTransaccion(() -> servicio.darDeBaja(RESPONSABLE, "56087406", "t"));
        em.clear();
        assertEquals(EstadoChofer.DADO_DE_BAJA, chofer("56087406").getEstado());
        assertTrue(servicio.listar(RESPONSABLE, "t").isEmpty());
        // ya no tiene el rol de chofer de la empresa (ni para el login de la app movil)
        assertTrue(roles().rolesDe("56087406").isEmpty());

        // se vuelve a agregar con el alta de siempre: se reactiva la misma asociacion
        assertEquals(Resultado.CONFIRMAR,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t")).resultado());
        assertEquals(Resultado.ALTA_CONFIRMADA,
                enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t")).resultado());
        em.clear();
        assertEquals(id, chofer("56087406").getId());
        assertEquals(EstadoChofer.HABILITADO, chofer("56087406").getEstado());
        assertEquals(1, servicio.listar(RESPONSABLE, "t").size());
    }

    @Test
    void unChoferDadoDeBajaConLaPdiCaidaVuelvePendiente() throws Exception {
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.darDeBaja(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> em.find(Ciudadano.class, "56087406").setEstado(EstadoValidacion.PENDIENTE_VALIDACION));

        pdi.caida = true;
        assertEquals(Resultado.ASOCIADO_PENDIENTE,
                enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t")).resultado());
        em.clear();
        assertEquals(EstadoChofer.PENDIENTE_VALIDACION, chofer("56087406").getEstado());
        assertEquals(1, inyectarEm(new ChoferDAO()).buscarPorCi("56087406").size());
    }

    @Test
    void noSeDaDeBajaAUnChoferConUnViajeAsignadoNiAUnoQueNoEsDeLaEmpresa() throws Exception {
        enTransaccion(() -> servicio.consultar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> servicio.confirmar(RESPONSABLE, "56087406", "t"));
        enTransaccion(() -> {
            Vehiculo vehiculo = new Vehiculo("SBA1234", "Scania", "R 450", 9000, 26000, empresa);
            em.persist(vehiculo);
            Guia guia = new Guia("GV-1", "Agropecuario", 18000, LocalDate.now(), "Montevideo", "Paysandu", empresa);
            guia.asignar(vehiculo, chofer("56087406"));
            em.persist(guia);
        });

        ConflictoException conflicto = assertThrows(ConflictoException.class,
                () -> enTransaccion(() -> servicio.darDeBaja(RESPONSABLE, "56087406", "t")));
        assertEquals("CHOFER_CON_VIAJE", conflicto.getCodigo());
        assertEquals(EstadoChofer.HABILITADO, chofer("56087406").getEstado());

        // terminado el viaje ya se lo puede dar de baja
        enTransaccion(() -> em.find(Guia.class, "GV-1").cambiarEstado(EstadoGuia.FINALIZADA));
        enTransaccion(() -> servicio.darDeBaja(RESPONSABLE, "56087406", "t"));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.darDeBaja(RESPONSABLE, "56087406", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.darDeBaja(RESPONSABLE, "12345678", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.darDeBaja("11111111", "56087406", "t"));
    }

    // ===== helpers =====

    private RolesService roles() throws Exception {
        RolesService roles = new RolesService();
        set(roles, "responsables", inyectarEm(new ResponsableDAO()));
        set(roles, "choferes", inyectarEm(new ChoferDAO()));
        return roles;
    }

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
