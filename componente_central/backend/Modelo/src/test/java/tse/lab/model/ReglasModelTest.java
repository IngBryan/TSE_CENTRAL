package tse.lab.model;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tse.lab.reglas.ConfiguracionReglas;

class ReglasModelTest {
    @Test
    void unaRevisionConservaSuAuditoriaYLaPublicacionDelOutbox() {
        var config = ConfiguracionReglas.inicial();
        var revision = new RevisionReglas(config, "corr-1");
        assertEquals("corr-1", revision.getCorrelationId());
        assertTrue(revision.pendiente());
        assertFalse(revision.datos().isPublicada());
        revision.publicada();
        assertFalse(revision.pendiente());
        assertTrue(revision.datos().isPublicada());
        assertEquals(config.getFechaActualizacion(), revision.datos().getFechaActualizacion());
        assertEquals(config.getUsuario(), revision.datos().getUsuario());
        assertEquals(config.getFundamento(), revision.datos().getFundamento());
        var vigente = new ReglasVigentes(1);
        vigente.avanzar(2);
        assertEquals(2, vigente.getRevision());
    }

    @Test
    void unPesajeMantieneLaPrimeraRevisionYRechazaRevisionesInvalidas() {
        var pesaje = new EventoPesajes();
        assertThrows(IllegalArgumentException.class, () -> pesaje.fijarRevisionReglas(0));
        assertEquals(3, pesaje.fijarRevisionReglas(3));
        assertEquals(3, pesaje.fijarRevisionReglas(9));
        assertThrows(IllegalArgumentException.class, () -> pesaje.fijarRevisionReglas(-1));
    }

    @Test
    void laEvidenciaSoloCorrespondeAlMismoEventoYSePuedeSerializar() throws Exception {
        Instant fecha = Instant.parse("2026-11-07T00:00:00Z");
        var evidencia = new EvidenciaReglas("SBC1234", fecha, "PESAJE", 2, 30000L);
        assertTrue(evidencia.corresponde("SBC1234", fecha, "PESAJE"));
        assertFalse(evidencia.corresponde("OTRA", fecha, "PESAJE"));
        assertFalse(evidencia.corresponde("SBC1234", fecha.plusSeconds(1), "PESAJE"));
        assertFalse(evidencia.corresponde("SBC1234", fecha, "TRACKING"));
        var caso = new CasoFiscalizacion();
        caso.getEvidenciasReglas().add(evidencia);
        var bytes = new ByteArrayOutputStream();
        try (var salida = new ObjectOutputStream(bytes)) { salida.writeObject(caso); }
        try (var entrada = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var copia = (CasoFiscalizacion) entrada.readObject();
            var guardada = copia.getEvidenciasReglas().getFirst();
            assertTrue(guardada.corresponde("SBC1234", fecha, "PESAJE"));
            assertEquals(2, guardada.getRevision());
            assertEquals(30000L, guardada.getPesoBaseKg());
        }
    }

    @Test
    void validaLosLimitesYLosDatosObligatoriosDeLaConfiguracion() {
        assertThrows(IllegalArgumentException.class, () -> config(0, BigDecimal.ZERO, 0, 1, 1, "2026-11-07T00:00:00Z", "a", "f"));
        assertThrows(IllegalArgumentException.class, () -> config(1, null, 0, 1, 1, "2026-11-07T00:00:00Z", "a", "f"));
        assertThrows(IllegalArgumentException.class, () -> config(1, BigDecimal.ZERO, -1, 1, 1, "2026-11-07T00:00:00Z", "a", "f"));
        assertThrows(IllegalArgumentException.class, () -> config(1, BigDecimal.ZERO, 0, 10001, 1, "2026-11-07T00:00:00Z", "a", "f"));
        for (int segundos : new int[] {0, 601})
            assertThrows(IllegalArgumentException.class, () -> config(1, BigDecimal.ZERO, 0, 1, segundos, "2026-11-07T00:00:00Z", "a", "f"));
        assertThrows(RuntimeException.class, () -> config(1, BigDecimal.ZERO, 0, 1, 1, "invalida", "a", "f"));
        assertThrows(NullPointerException.class, () -> config(1, BigDecimal.ZERO, 0, 1, 1, null, "a", "f"));
        assertThrows(NullPointerException.class, () -> config(1, BigDecimal.ZERO, 0, 1, 1, "2026-11-07T00:00:00Z", null, "f"));
        assertThrows(NullPointerException.class, () -> config(1, BigDecimal.ZERO, 0, 1, 1, "2026-11-07T00:00:00Z", "a", null));
        var maximo = config(2, new BigDecimal("20.00"), 5000, 10000, 600, "2026-11-07T00:00:00.000Z", "a", "f");
        assertEquals("2026-11-07T00:00:00Z", maximo.getFechaActualizacion());
        assertEquals(600, maximo.getVentanaDesvioSegundos());
        assertEquals(10000, maximo.getDistanciaDesvioMetros());
        assertEquals(5000, maximo.getToleranciaCargaKg());
    }

    private ConfiguracionReglas config(long revision, BigDecimal porcentaje, int kg, int metros, int segundos,
            String fecha, String usuario, String fundamento) {
        return new ConfiguracionReglas(revision, porcentaje, kg, metros, segundos, fecha, usuario, fundamento, false);
    }
}
