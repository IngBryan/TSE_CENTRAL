package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

// Guias, sus modificaciones y los eventos de viaje que registra el chofer desde la app movil.
class GuiaYViajeTest {

    private final Empresa empresa = new Empresa();
    private final Vehiculo vehiculo = new Vehiculo("SBC1234", "Volvo", "FH", 9_000L, 25_000L, empresa);
    private final Chofer chofer = new Chofer(new Ciudadano("11111111", "chofer@example.com", null), empresa);

    private Guia guia() {
        return new Guia("GV-1", "Agro", 18_000L, LocalDate.of(2026, 10, 19), "Montevideo", "Salto", empresa);
    }

    @Test
    void unaGuiaNuevaNoTieneVehiculoNiChoferYEstaSinAsignar() {
        Guia guia = guia();

        assertEquals(EstadoGuia.SIN_ASIGNAR, guia.getEstado());
        assertNull(guia.getVehiculo());
        assertNull(guia.getChofer());
        assertNull(guia.getFechaAsignacion());
        assertNull(guia.getVersion());
    }

    @Test
    void asignarLaDejaAsignadaConVehiculoChoferYFecha() {
        Guia guia = guia();

        guia.asignar(vehiculo, chofer);

        assertEquals(EstadoGuia.ASIGNADA, guia.getEstado());
        assertSame(vehiculo, guia.getVehiculo());
        assertSame(chofer, guia.getChofer());
        assertNotNull(guia.getFechaAsignacion());

        guia.cambiarEstado(EstadoGuia.EN_CURSO);
        assertEquals(EstadoGuia.EN_CURSO, guia.getEstado());
    }

    @Test
    void losDatosDeLaGuiaSePuedenEditar() {
        Guia guia = new Guia();
        Empresa otra = new Empresa();

        guia.setId("GV-2");
        guia.setRubroCliente("Forestal");
        guia.setTipoCarga("Troncos");
        guia.setPesoCargaKg(20_000L);
        guia.setFecha(LocalDate.of(2026, 10, 20));
        guia.setOrigen("Rivera");
        guia.setDestino("Fray Bentos");
        guia.setEmpresa(otra);

        assertEquals("GV-2", guia.getId());
        assertEquals("Forestal", guia.getRubroCliente());
        assertEquals("Troncos", guia.getTipoCarga());
        assertEquals(20_000L, guia.getPesoCargaKg());
        assertEquals(LocalDate.of(2026, 10, 20), guia.getFecha());
        assertEquals("Rivera", guia.getOrigen());
        assertEquals("Fray Bentos", guia.getDestino());
        assertSame(otra, guia.getEmpresa());
    }

    @Test
    void laModificacionRegistraQuienQueYLasVersiones() {
        Guia guia = guia();

        ModificacionGuia modificacion = new ModificacionGuia(guia, TipoModificacionGuia.EDICION, "22222222",
                "destino: Salto -> Paysandu", null, 1L, 2L);

        assertNull(modificacion.getId());
        assertSame(guia, modificacion.getGuia());
        assertEquals(TipoModificacionGuia.EDICION, modificacion.getTipo());
        assertEquals("22222222", modificacion.getCiResponsable());
        assertEquals("destino: Salto -> Paysandu", modificacion.getCambios());
        assertNull(modificacion.getMotivo());
        assertEquals(1L, modificacion.getVersionAnterior());
        assertEquals(2L, modificacion.getVersionNueva());
        // con la precision que guarda la base
        assertEquals(modificacion.getFecha().truncatedTo(ChronoUnit.MICROS), modificacion.getFecha());
        assertNull(new ModificacionGuia().getGuia());
    }

    @Test
    void elEventoDeViajeGuardaLosDatosDelMovilSinResultado() {
        Guia guia = guia();
        Instant dispositivo = Instant.parse("2026-10-19T12:00:00Z");
        byte[] foto = { 1, 2, 3 };

        EventoViaje evento = new EventoViaje("uuid-1", guia, "11111111", TipoEventoViaje.CARGA_PARCIAL, dispositivo,
                "Carga en Durazno", -33.38, -56.52, foto);
        evento.setVersionGuiaDispositivo(3L);

        assertEquals("uuid-1", evento.getIdEvento());
        assertSame(guia, evento.getGuia());
        assertEquals("11111111", evento.getCiChofer());
        assertEquals(TipoEventoViaje.CARGA_PARCIAL, evento.getTipo());
        assertEquals(dispositivo, evento.getFechaHoraDispositivo());
        assertEquals(evento.getFechaRecepcion().truncatedTo(ChronoUnit.MICROS), evento.getFechaRecepcion());
        assertEquals("Carga en Durazno", evento.getDescripcion());
        assertEquals(-33.38, evento.getLatitud());
        assertEquals(-56.52, evento.getLongitud());
        assertArrayEquals(foto, evento.getFoto());
        assertEquals(3L, evento.getVersionGuiaDispositivo());
        assertNull(evento.getResultado());
        assertNull(new EventoViaje().getIdEvento());
    }

    @Test
    void elResultadoDelEventoQuedaRegistradoConElEstadoDeLaGuia() {
        EventoViaje evento = new EventoViaje("uuid-2", guia(), "11111111", TipoEventoViaje.FIN, Instant.now(), null,
                null, null, null);

        evento.registrarResultado(ResultadoEventoViaje.CONFLICTO, "VIAJE_FINALIZADO", "La guia ya finalizo",
                EstadoGuia.FINALIZADA, 7L);

        assertEquals(ResultadoEventoViaje.CONFLICTO, evento.getResultado());
        assertEquals("VIAJE_FINALIZADO", evento.getCodigo());
        assertEquals("La guia ya finalizo", evento.getDetalle());
        assertEquals(EstadoGuia.FINALIZADA, evento.getEstadoGuiaResultante());
        assertEquals(7L, evento.getVersionGuiaResultante());
    }
}
