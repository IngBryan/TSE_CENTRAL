package tse.lab.adaptadorpdi;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

// Regla de edad de CU-03: para usar carga.uy hay que tener 18 anios cumplidos (hora de Uruguay).
public final class MayoriaDeEdad {

    static final int EDAD_MINIMA = 18;
    private static final ZoneId ZONA = ZoneId.of("America/Montevideo");

    private MayoriaDeEdad() {
    }

    public static boolean cumple(LocalDate fechaNacimiento) {
        return fechaNacimiento != null && cumple(fechaNacimiento, LocalDate.now(ZONA));
    }

    static boolean cumple(LocalDate fechaNacimiento, LocalDate hoy) {
        return Period.between(fechaNacimiento, hoy).getYears() >= EDAD_MINIMA;
    }
}
