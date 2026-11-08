package tse.lab.fiscalizacionautomatica.ruleengine.trips;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResultadoAsociacionGpsGuiaTest {

    @Test
    void anAssociationNeedsItsGuide() {
        assertThrows(IllegalArgumentException.class, () -> ResultadoAsociacionGpsGuia.asociado(null));
        assertThrows(IllegalArgumentException.class, () -> ResultadoAsociacionGpsGuia.asociado(" "));
    }

    @Test
    void onlyAnAssociationHasAGuide() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResultadoAsociacionGpsGuia(ResultadoAsociacionGpsGuia.Estado.SIN_GUIA, "G1"));
        assertThrows(NullPointerException.class, () -> new ResultadoAsociacionGpsGuia(null, null));
    }

    @Test
    void itCorrespondsOnlyToItsGuide() {
        assertTrue(ResultadoAsociacionGpsGuia.asociado("G1").correspondeA("G1"));
        assertFalse(ResultadoAsociacionGpsGuia.asociado("G1").correspondeA("G2"));
        assertFalse(ResultadoAsociacionGpsGuia.sinGuia().correspondeA("G1"));
    }
}
