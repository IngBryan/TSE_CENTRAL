package tse.lab.frontofficeciudadanos.service.portalpublico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.Fila;
import tse.lab.frontofficeciudadanos.service.portalpublico.Agregacion.FilaPublica;

class AgregacionTest {

    private static Fila fila(String clave, long cantidad, Long... empresas) {
        return new Fila(clave, clave, Set.of(empresas), cantidad, cantidad * 1000);
    }

    private static List<Boolean> reservadas(List<FilaPublica> filas) {
        return filas.stream().map(FilaPublica::reservado).toList();
    }

    @Test
    void conTresEmpresasOMasSePublicaTodo() {
        List<FilaPublica> r = Agregacion.proteger(List.of(fila("a", 10, 1L, 2L, 3L), fila("b", 4, 2L, 3L, 4L)), 3);

        assertEquals(List.of(false, false), reservadas(r));
        assertEquals(10, r.get(0).cantidad());
    }

    @Test
    void unaFilaChicaSeReservaJuntoConLaMasChicaDeLasVisibles() {
        // "c" tiene una sola empresa: sola se deduciria restando del total, asi que se reserva tambien "b"
        List<FilaPublica> r = Agregacion.proteger(
                List.of(fila("a", 10, 1L, 2L, 3L), fila("b", 4, 2L, 3L, 4L), fila("c", 2, 5L)), 3);

        assertEquals(List.of(false, true, true), reservadas(r));
        assertNull(r.get(2).cantidad());
        assertNull(r.get(2).kg());
    }

    @Test
    void siLasReservadasYaJuntanElUmbralNoSeReservaNadaMas() {
        List<FilaPublica> r = Agregacion.proteger(
                List.of(fila("a", 10, 1L, 2L, 3L), fila("b", 2, 4L, 5L), fila("c", 1, 6L)), 3);

        assertEquals(List.of(false, true, true), reservadas(r));
    }

    @Test
    void siNoAlcanzanNiJuntandoTodoSeReservaTodo() {
        List<FilaPublica> r = Agregacion.proteger(List.of(fila("a", 5, 1L), fila("b", 3, 1L, 2L)), 3);

        assertEquals(List.of(true, true), reservadas(r));
    }

    @Test
    void lasFilasEnCeroSePublicanYNoSeUsanParaCompletar() {
        List<FilaPublica> r = Agregacion.proteger(List.of(fila("enero", 0), fila("febrero", 3, 1L)), 3);

        assertFalse(r.get(0).reservado());
        assertEquals(0, r.get(0).cantidad());
        assertTrue(r.get(1).reservado());
    }

    @Test
    void unTotalSePublicaConElUmbralOEnCero() {
        assertTrue(Agregacion.publicable(Set.of(1L, 2L, 3L), 7, 3));
        assertFalse(Agregacion.publicable(Set.of(1L, 2L), 7, 3));
        assertTrue(Agregacion.publicable(Set.of(), 0, 3));
    }

    @Test
    void porFilaSoloSeReservaLoQueNoJuntaElUmbral() {
        List<FilaPublica> r = Agregacion.protegerPorFila(
                List.of(fila("a", 3, 1L, 2L, 3L), fila("b", 1, 1L), fila("c", 0)), 3);

        assertEquals(List.of(false, true, false), reservadas(r));
        assertEquals(3, r.get(0).cantidad());
    }
}
