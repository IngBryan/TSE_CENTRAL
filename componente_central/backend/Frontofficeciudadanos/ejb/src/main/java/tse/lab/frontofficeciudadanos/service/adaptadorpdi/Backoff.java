package tse.lab.frontofficeciudadanos.service.adaptadorpdi;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

// Espera exponencial para reintentar contra la PDI: base, 2*base, 4*base... hasta un tope.
final class Backoff {

    private Backoff() {
    }

    // Espera antes del reintento n (n >= 1): base * 2^(n-1), sin pasar de max.
    static Duration exponencial(int n, Duration base, Duration max) {
        long ms = base.toMillis();
        for (int i = 1; i < n && ms < max.toMillis(); i++) {
            ms *= 2;
        }
        return Duration.ofMillis(Math.min(ms, max.toMillis()));
    }

    // Entre la mitad y el total de la espera, al azar: si muchas consultas fallan juntas no
    // reintentan todas en el mismo instante.
    static Duration conJitter(Duration espera) {
        long ms = espera.toMillis();
        return Duration.ofMillis(ms / 2 + ThreadLocalRandom.current().nextLong(ms / 2 + 1));
    }
}
