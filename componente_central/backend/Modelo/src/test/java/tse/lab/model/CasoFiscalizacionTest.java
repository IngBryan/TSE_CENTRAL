package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Ciclo de vida de los casos de fiscalizacion (letra 3.5 b): solo las transiciones previstas.
class CasoFiscalizacionTest {

    private static final Map<EstadoCaso, Set<EstadoCaso>> PREVISTAS = Map.of(
            EstadoCaso.DETECTADA, Set.of(EstadoCaso.NOTIFICADA),
            EstadoCaso.NOTIFICADA, Set.of(EstadoCaso.EN_DESCARGO),
            EstadoCaso.EN_DESCARGO, Set.of(EstadoCaso.CONFIRMADA, EstadoCaso.DESESTIMADA),
            EstadoCaso.CONFIRMADA, Set.of(),
            EstadoCaso.DESESTIMADA, Set.of());

    @Test
    void nuevoCasoNaceDetectadoConFechaDeDeteccion() {
        CasoFiscalizacion caso = new CasoFiscalizacion("Exceso de carga", "EXCESO_CARGA", null);
        assertEquals(EstadoCaso.DETECTADA, caso.getEstado());
        assertNotNull(caso.getFechaDeteccion());
    }

    @Test
    void todasLasCombinacionesDeEstados() {
        for (EstadoCaso desde : EstadoCaso.values()) {
            for (EstadoCaso hacia : EstadoCaso.values()) {
                boolean prevista = PREVISTAS.get(desde).contains(hacia);
                assertEquals(prevista, desde.puedePasarA(hacia), desde + " -> " + hacia);
                CasoFiscalizacion caso = casoEn(desde);
                if (prevista) {
                    assertDoesNotThrow(() -> caso.cambiarEstado(hacia));
                    assertEquals(hacia, caso.getEstado());
                } else {
                    assertThrows(IllegalStateException.class, () -> caso.cambiarEstado(hacia), desde + " -> " + hacia);
                    assertEquals(desde, caso.getEstado(), "el estado no cambia si la transicion se rechaza");
                }
            }
        }
    }

    @Test
    void soloEnDescargoAdmiteResolucion() {
        for (EstadoCaso e : EstadoCaso.values()) {
            assertEquals(e == EstadoCaso.EN_DESCARGO, e.admiteResolucion(), e.name());
        }
        assertTrue(EstadoCaso.CONFIRMADA.esFinal());
        assertTrue(EstadoCaso.DESESTIMADA.esFinal());
        assertFalse(EstadoCaso.EN_DESCARGO.esFinal());
        assertFalse(EstadoCaso.DETECTADA.puedePasarA(null));
    }

    // Lleva un caso nuevo hasta el estado pedido por el camino previsto
    static CasoFiscalizacion casoEn(EstadoCaso estado) {
        CasoFiscalizacion caso = new CasoFiscalizacion("d", "t", null);
        List<EstadoCaso> camino = switch (estado) {
            case DETECTADA -> List.of();
            case NOTIFICADA -> List.of(EstadoCaso.NOTIFICADA);
            case EN_DESCARGO -> List.of(EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO);
            case CONFIRMADA, DESESTIMADA -> List.of(EstadoCaso.NOTIFICADA, EstadoCaso.EN_DESCARGO, estado);
        };
        camino.forEach(caso::cambiarEstado);
        return caso;
    }
}
