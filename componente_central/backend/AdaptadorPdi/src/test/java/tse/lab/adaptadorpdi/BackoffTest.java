package tse.lab.adaptadorpdi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class BackoffTest {

    private static final Duration BASE = Duration.ofMillis(500);
    private static final Duration MAX = Duration.ofSeconds(4);

    @Test
    void laEsperaSeDuplicaEnCadaReintento() {
        assertEquals(Duration.ofMillis(500), Backoff.exponencial(1, BASE, MAX));
        assertEquals(Duration.ofSeconds(1), Backoff.exponencial(2, BASE, MAX));
        assertEquals(Duration.ofSeconds(2), Backoff.exponencial(3, BASE, MAX));
    }

    @Test
    void noPasaDelTope() {
        assertEquals(MAX, Backoff.exponencial(4, BASE, MAX));
        assertEquals(MAX, Backoff.exponencial(1000, BASE, MAX));
    }

    @Test
    void elJitterQuedaEntreLaMitadYElTotal() {
        for (int i = 0; i < 100; i++) {
            long ms = Backoff.conJitter(Duration.ofSeconds(2)).toMillis();
            assertTrue(ms >= 1000 && ms <= 2000, "espera " + ms);
        }
    }
}
