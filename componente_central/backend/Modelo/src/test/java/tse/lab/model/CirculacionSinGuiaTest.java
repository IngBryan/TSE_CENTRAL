package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class CirculacionSinGuiaTest {
    private static final Instant T = Instant.parse("2026-11-01T12:00:00Z");
    private final Vehiculo vehiculo = new Vehiculo();

    private CirculacionSinGuia pendiente() {
        return new CirculacionSinGuia(vehiculo, T, -34.0, -56.0, T.plusSeconds(1));
    }

    @Test
    void startsPendingAtTheFirstPosition() {
        CirculacionSinGuia c = pendiente();

        assertSame(vehiculo, c.getVehiculo());
        assertEquals(EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO, c.getEstado());
        assertEquals(T, c.getFechaInicio());
        assertEquals(T, c.getUltimaMedicion());
        assertEquals(T, c.getUltimoMovimiento());
        assertEquals(T.plusSeconds(1), c.getUltimaRecepcion());
        assertEquals(-34.0, c.getLatitudActual());
        assertEquals(-56.0, c.getLongitudActual());
        assertEquals(-34.0, c.getLatitudMovimiento());
        assertEquals(-56.0, c.getLongitudMovimiento());
        assertNull(c.getId());
        assertNull(c.getCaso());
        assertNull(c.getFechaFin());
        assertNull(c.getMotivoFin());
    }

    @Test
    void restartingAPendingOneMovesItsStart() {
        CirculacionSinGuia c = pendiente();

        c.reiniciarPendiente(T.plusSeconds(60), -34.1, -56.1, T.plusSeconds(61));

        assertEquals(T.plusSeconds(60), c.getFechaInicio());
        assertEquals(T.plusSeconds(60), c.getUltimoMovimiento());
        assertEquals(-34.1, c.getLatitudMovimiento());
    }

    @Test
    void confirmingLinksTheCaseOnlyOnce() {
        CirculacionSinGuia c = pendiente();
        CasoFiscalizacion caso = new CasoFiscalizacion();

        c.confirmar(caso, T.plusSeconds(30), -34.1, -56.1, T.plusSeconds(31));

        assertSame(caso, c.getCaso());
        assertEquals(EstadoCirculacionSinGuia.ACTIVA, c.getEstado());
        assertThrows(IllegalStateException.class,
                () -> c.confirmar(caso, T.plusSeconds(40), -34.1, -56.1, T.plusSeconds(41)));
        assertThrows(IllegalStateException.class,
                () -> c.reiniciarPendiente(T.plusSeconds(40), -34.1, -56.1, T.plusSeconds(41)));
    }

    @Test
    void aCaseIsRequiredToConfirm() {
        CirculacionSinGuia c = pendiente();

        assertThrows(IllegalStateException.class,
                () -> c.confirmar(null, T.plusSeconds(30), -34.1, -56.1, T.plusSeconds(31)));
    }

    @Test
    void aStoppedVehicleKeepsItsLastMovement() {
        CirculacionSinGuia c = pendiente();

        c.actualizar(T.plusSeconds(30), -34.2, -56.2, T.plusSeconds(31), false);

        assertEquals(T.plusSeconds(30), c.getUltimaMedicion());
        assertEquals(-34.2, c.getLatitudActual());
        assertEquals(-56.2, c.getLongitudActual());
        assertEquals(T, c.getUltimoMovimiento());
        assertEquals(-34.0, c.getLatitudMovimiento());
    }

    @Test
    void anOutOfOrderPositionIsRejected() {
        CirculacionSinGuia c = pendiente();

        assertThrows(IllegalArgumentException.class,
                () -> c.actualizar(T.minusSeconds(1), -34.2, -56.2, T, true));
    }

    @Test
    void finishingKeepsTheFirstEnd() {
        CirculacionSinGuia c = pendiente();

        c.finalizar(MotivoFinCirculacionSinGuia.SIN_SENAL_GPS, T.plusSeconds(90));
        c.finalizar(MotivoFinCirculacionSinGuia.GUIA_INICIADA, T.plusSeconds(120));

        assertEquals(EstadoCirculacionSinGuia.FINALIZADA, c.getEstado());
        assertEquals(MotivoFinCirculacionSinGuia.SIN_SENAL_GPS, c.getMotivoFin());
        assertEquals(T.plusSeconds(90), c.getFechaFin());
    }

    @Test
    void itCannotEndBeforeItStarted() {
        CirculacionSinGuia c = pendiente();

        assertThrows(IllegalArgumentException.class,
                () -> c.finalizar(MotivoFinCirculacionSinGuia.BRECHA_TEMPORAL, T.minusSeconds(1)));
        assertEquals(EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO, c.getEstado());
    }

    @Test
    void theEvidenceKeepsThePairAndTheRoutesUsed() {
        Instant pareja = T.plusSeconds(10);
        EvidenciaGeometricaDesvio e = new EvidenciaGeometricaDesvio("SBC1234", T, pareja, -34.1, -56.1, -34.2, -56.2,
                2500, 2600, 2000, 30, "sha-a,sha-b");

        assertEquals("SBC1234", e.getMatricula());
        assertEquals(T, e.getFechaHora());
        assertEquals(pareja, e.getFechaPareja());
        assertEquals(-34.1, e.getLatConfirmante());
        assertEquals(-56.1, e.getLonConfirmante());
        assertEquals(-34.2, e.getLatPareja());
        assertEquals(-56.2, e.getLonPareja());
        assertEquals(2500, e.getDistanciaConfirmante());
        assertEquals(2600, e.getDistanciaPareja());
        assertEquals(2000, e.getUmbralMetros());
        assertEquals(30, e.getVentanaSegundos());
        assertEquals("sha-a,sha-b", e.getRutasSha());
        assertEquals(0, new CasoFiscalizacion().getEvidenciasDesvio().size());
    }
}
