package tse.lab.model;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.model.EvidenciaHabilitacion.EstadoDocumento;

class EvidenciaHabilitacionTest {
    private static final LocalDate DIA = LocalDate.of(2026, 11, 6);
    private static final Instant FECHA = Instant.parse("2026-11-07T02:59:59Z"); // Aún 6 en Uruguay
    private final Vehiculo vehiculo = new Vehiculo();

    private PermisoCirculacion permiso(LocalDate desde, LocalDate hasta) {
        return new PermisoCirculacion("PNC-1", desde, hasta, vehiculo);
    }
    private ITV itv(LocalDate desde, LocalDate hasta) { return new ITV(desde, hasta, vehiculo); }

    @Test
    void emisionYVencimientoSonInclusivosEnUruguay() {
        var permiso = permiso(DIA, DIA.plusDays(1));
        var itvs = List.of(itv(DIA.minusYears(1), DIA));
        assertFalse(EvidenciaHabilitacion.evaluar("ABC1234", FECHA, permiso, itvs).sinHabilitacion());
        var siguienteDia = EvidenciaHabilitacion.evaluar("ABC1234", FECHA.plusSeconds(1), permiso, itvs);
        assertEquals(EstadoDocumento.VENCIDO, siguienteDia.getEstadoItv());
        assertTrue(siguienteDia.sinHabilitacion());
    }

    @Test
    void cadaDocumentoAusenteOVencidoOriginaLaInfraccion() {
        var vigente = permiso(DIA.minusYears(1), DIA);
        var inspeccion = List.of(itv(DIA.minusYears(1), DIA));
        var sinPermiso = EvidenciaHabilitacion.evaluar("ABC1234", FECHA, null, inspeccion);
        assertTrue(sinPermiso.sinHabilitacion());
        assertEquals(EstadoDocumento.SIN_REGISTRO, sinPermiso.getEstadoPermiso());
        assertEquals(EstadoDocumento.VIGENTE, sinPermiso.getEstadoItv());
        assertTrue(EvidenciaHabilitacion.evaluar("ABC1234", FECHA, vigente, List.of()).sinHabilitacion());
        var vencido = EvidenciaHabilitacion.evaluar("ABC1234", FECHA,
                permiso(DIA.minusYears(1), DIA.minusDays(1)), inspeccion);
        assertEquals(EstadoDocumento.VENCIDO, vencido.getEstadoPermiso());
        assertTrue(vencido.sinHabilitacion());
        var ninguno = EvidenciaHabilitacion.evaluar("ABC1234", FECHA, null, List.of());
        assertEquals(EstadoDocumento.SIN_REGISTRO, ninguno.getEstadoItv());
        assertTrue(ninguno.sinHabilitacion());
    }

    @Test
    void documentosFuturosNoHabilitanElEventoAnterior() {
        var futura = itv(DIA.plusDays(1), DIA.plusYears(1));
        var p = permiso(DIA.plusDays(1), DIA.plusYears(1));
        var prueba = EvidenciaHabilitacion.evaluar("ABC1234", FECHA, p, List.of(futura));
        assertEquals(EstadoDocumento.AUN_NO_VIGENTE, prueba.getEstadoPermiso());
        assertEquals(EstadoDocumento.AUN_NO_VIGENTE, prueba.getEstadoItv());
        assertTrue(prueba.sinHabilitacion());
        assertEquals(DIA, EvidenciaHabilitacion.evaluar("ABC1234", FECHA, p,
                List.of(futura, itv(DIA.minusYears(1), DIA))).getVencimientoItv());
    }

    @Test
    void copiaEsSerializableYNoCambiaAlRenovarElPermiso() throws Exception {
        var p = permiso(DIA.minusYears(1), DIA.minusDays(1));
        var prueba = EvidenciaHabilitacion.evaluar("ABC1234", FECHA, p, List.of()).conOrigen("TRACKING");
        p.setFechaVencimiento(DIA.plusYears(1));
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) { out.writeObject(prueba); }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            assertEquals(prueba, in.readObject());
        }
        assertEquals(EstadoDocumento.VENCIDO, prueba.getEstadoPermiso());
        assertEquals("TRACKING", EvidenciaHabilitacionVista.de(prueba).origen());
    }
}
