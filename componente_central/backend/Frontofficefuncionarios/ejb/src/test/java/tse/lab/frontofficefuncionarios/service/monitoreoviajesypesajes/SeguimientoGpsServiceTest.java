package tse.lab.frontofficefuncionarios.service.monitoreoviajesypesajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervaloGuiaGps;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.IntervalosGuiasGpsRemote;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;
import tse.lab.frontofficeciudadanos.service.rutas.RutasAutorizadasRemote;
import tse.lab.frontofficefuncionarios.data.GuiaDAO;
import tse.lab.frontofficefuncionarios.service.DatosInvalidosException;
import tse.lab.frontofficefuncionarios.service.NoAutorizadoException;
import tse.lab.frontofficefuncionarios.service.RecursoNoEncontradoException;
import tse.lab.frontofficefuncionarios.service.autenticacion.AutorizacionFuncionario;
import tse.lab.model.Chofer;
import tse.lab.model.Ciudadano;
import tse.lab.model.Empresa;
import tse.lab.model.Funcionario;
import tse.lab.model.Guia;
import tse.lab.model.Vehiculo;

class SeguimientoGpsServiceTest {
    private static final Instant T = Instant.parse("2026-10-20T12:00:00Z");
    private SeguimientoGpsService service;
    private Guia guia;

    @BeforeEach
    void preparar() throws Exception {
        var empresa = new Empresa(1L, "Transporte", "TSA", "Montevideo", null);
        var persona = new Ciudadano("12345678", "chofer@example.com", "sub");
        var chofer = new Chofer(persona, empresa);
        var vehiculo = new Vehiculo("SBC1234", "Marca", "Modelo", 8000, 20000, empresa);
        guia = new Guia("G1", "Carga", 1000, LocalDate.of(2026, 10, 20), "A", "B", empresa);
        guia.asignar(vehiculo, chofer);

        service = new SeguimientoGpsService();
        set("autorizacion", new AutorizacionFuncionario() {
            @Override public Funcionario funcionario(String ci, String correlationId) throws NoAutorizadoException {
                if (!"12345678".equals(ci)) throw new NoAutorizadoException("No es funcionario");
                return new Funcionario(ci, "funcionario@mtop.gub.uy");
            }
        });
        set("guias", new GuiaDAO() {
            @Override public Guia buscarPorId(String id) { return "G1".equals(id) ? guia : null; }
        });
        set("intervalos", (IntervalosGuiasGpsRemote) (matricula, desde, hasta) ->
                List.of(new IntervaloGuiaGps("G1", "SBC1234", "EN_CURSO",
                        T.minusSeconds(60), T.minusSeconds(30), null)));
        set("rutas", new RutasAutorizadasRemote() {
            @Override
            public List<DatosRutaAutorizada> obtenerPorGuia(String idGuia) {
                return List.of(new DatosRutaAutorizada(42L, 1, "hash", 1200,
                        "{\"type\":\"LineString\"}"));
            }

            @Override
            public List<DatosRutaAutorizada> obtenerPorHuellas(List<String> huellas) {
                throw new AssertionError(
                        "El seguimiento no debe consultar rutas de evidencias históricas");
            }
        });
        set("tracking", new MongoSeguimientoDAO() {
            @Override public List<PuntoGpsSeguimiento> buscar(String matricula, Instant desde, Instant hasta,
                    int limite) {
                return List.of(p(0), p(1), p(2));
            }
            @Override public List<PuntoGpsSeguimiento> recientes(String matricula, Instant desde,
                    Instant hasta, int limite) {
                return List.of();
            }
        });
    }

    private static PuntoGpsSeguimiento p(long segundos) {
        return new PuntoGpsSeguimiento("evt" + segundos, 1, "SBC1234", -34.5, -56.2,
                T.plusSeconds(segundos));
    }

    private void set(String name, Object value) throws Exception {
        Field f = SeguimientoGpsService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    @Test
    void historialPaginaCandidatosSinRepetirYRetornaRutas() throws Exception {
        var pagina = service.historial("12345678", "G1", T.minusSeconds(5).toString(),
                T.plusSeconds(100).toString(), "2", "t");
        assertEquals(List.of(p(0), p(1)), pagina.posiciones());
        assertTrue(pagina.hayMas());
        assertEquals(T.plusSeconds(1).plusMillis(1).toString(), pagina.siguienteDesde());
        assertEquals(42L, service.rutas("12345678", "G1", "t").getFirst().getIdRuta());
    }

    @Test
    void rechazaAccesoAntesDeConsultarDatosYGuiaInvisible() throws Exception {
        assertThrows(NoAutorizadoException.class,
                () -> service.historial("NO", "G1", "X", "Y", null, "t"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.rutas("12345678", "G2", "t"));
    }

    @Test
    void validaRangosYLimites() {
        assertThrows(DatosInvalidosException.class,
                () -> service.historial("12345678", "G1", "X", "Y", "9999", "t"));
        assertThrows(DatosInvalidosException.class,
                () -> service.historial("12345678", "G1", T.toString(),
                        T.plusSeconds(32L * 24 * 3600).toString(), "5", "t"));
    }

    @Test
    void ultimaNoInventadaSiNoHayPosiciones() throws Exception {
        assertNull(service.ultima("12345678", "G1", "t").posicion());
    }

    private void historialInvalido(String desde, String hasta, String limite) {
        assertThrows(DatosInvalidosException.class,
                () -> service.historial("12345678", "G1", desde, hasta, limite, "t"));
    }

    @Test
    void anInvalidRangeOrLimitIsRejected() {
        String desde = T.toString();
        String hasta = T.plusSeconds(60).toString();

        historialInvalido(null, hasta, "5");
        historialInvalido(" ", hasta, "5");
        historialInvalido(desde, desde, "5");
        historialInvalido(hasta, desde, "5");
        historialInvalido(desde, hasta, "0");
        historialInvalido(desde, hasta, "abc");
    }

    @Test
    void withoutLimitItUsesTheDefaultOne() throws Exception {
        var pagina = service.historial("12345678", "G1", T.minusSeconds(5).toString(), T.plusSeconds(100).toString(),
                " ", "t");

        assertEquals(3, pagina.posiciones().size());
        assertNull(pagina.siguienteDesde());
    }

    @Test
    void theLastPositionIsTheNewestOfTheTrip() throws Exception {
        PuntoGpsSeguimiento otroViaje = new PuntoGpsSeguimiento("evt", 1, "SBC1234", -34.5, -56.2, T.minusSeconds(120));
        set("tracking", new MongoSeguimientoDAO() {
            @Override public List<PuntoGpsSeguimiento> recientes(String matricula, Instant desde, Instant hasta,
                    int limite) {
                return List.of(otroViaje, p(0));
            }
        });

        assertEquals(p(0), service.ultima("12345678", "G1", "t").posicion());
    }

    @Test
    void aRoutesFailureIsReportedAsUnavailable() throws Exception {
        set("rutas", new RutasAutorizadasRemote() {
            @Override public List<DatosRutaAutorizada> obtenerPorGuia(String idGuia) {
                throw new IllegalStateException("EJB down");
            }

            @Override public List<DatosRutaAutorizada> obtenerPorHuellas(List<String> huellas) {
                return List.of();
            }
        });

        assertThrows(SeguimientoNoDisponibleException.class, () -> service.rutas("12345678", "G1", "t"));
    }

    @Test
    void anIntervalsFailureIsReportedAsUnavailable() throws Exception {
        set("intervalos", (IntervalosGuiasGpsRemote) (matricula, desde, hasta) -> {
            throw new IllegalStateException("EJB down");
        });

        assertThrows(SeguimientoNoDisponibleException.class, () -> service.ultima("12345678", "G1", "t"));
    }

    @Test
    void aTripWithoutVehicleOrDriverIsNotVisible() {
        var chofer = guia.getChofer();
        guia.setChofer(null);
        assertThrows(RecursoNoEncontradoException.class, () -> service.rutas("12345678", "G1", "t"));

        guia.setChofer(chofer);
        guia.setVehiculo(null);
        assertThrows(RecursoNoEncontradoException.class, () -> service.rutas("12345678", "G1", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> service.rutas("12345678", null, "t"));
    }

    @Test
    void anUnassignedGuideIsNotATrip() {
        var vehiculo = guia.getVehiculo();
        var chofer = guia.getChofer();
        guia = new Guia("G1", "Carga", 1000, LocalDate.of(2026, 10, 20), "A", "B", new Empresa());
        guia.setVehiculo(vehiculo);
        guia.setChofer(chofer);

        assertThrows(RecursoNoEncontradoException.class, () -> service.rutas("12345678", "G1", "t"));
    }
}
