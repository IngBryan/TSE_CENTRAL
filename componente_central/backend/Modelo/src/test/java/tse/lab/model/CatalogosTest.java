package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

// Catalogos precargados (rubros y tipos de carga) y los rubros de una empresa
class CatalogosTest {

    @Test
    void rubroGuardaIdYNombre() {
        Rubro rubro = new Rubro(1L, "Agropecuario");

        assertAll(
                () -> assertEquals(1L, rubro.getId()),
                () -> assertEquals("Agropecuario", rubro.getNombre()));
    }

    @Test
    void rubroVacioNoTieneDatos() {
        Rubro rubro = new Rubro();

        assertAll(
                () -> assertNull(rubro.getId()),
                () -> assertNull(rubro.getNombre()));
    }

    @Test
    void tipoCargaGuardaIdYNombre() {
        TipoCarga tipo = new TipoCarga(3L, "Granos a granel");

        assertAll(
                () -> assertEquals(3L, tipo.getId()),
                () -> assertEquals("Granos a granel", tipo.getNombre()));
    }

    @Test
    void tipoCargaVacioNoTieneDatos() {
        TipoCarga tipo = new TipoCarga();

        assertAll(
                () -> assertNull(tipo.getId()),
                () -> assertNull(tipo.getNombre()));
    }

    @Test
    void empresaEmpiezaSinRubros() {
        assertAll(
                () -> assertNotNull(new Empresa().getRubros()),
                () -> assertTrue(new Empresa().getRubros().isEmpty()),
                () -> assertTrue(new Empresa(1L, "N", "R", "D", null).getRubros().isEmpty()));
    }

    @Test
    void setRubrosReemplazaLosRubrosSinCambiarLaColeccion() {
        Empresa empresa = new Empresa();
        Rubro agro = new Rubro(1L, "Agropecuario");
        Rubro forestal = new Rubro(2L, "Forestal");
        List<Rubro> coleccion = empresa.getRubros();

        empresa.setRubros(new ArrayList<>(List.of(agro)));
        empresa.setRubros(List.of(forestal));

        assertAll(
                () -> assertEquals(List.of(forestal), empresa.getRubros()),
                () -> assertSame(coleccion, empresa.getRubros()));
    }

    @Test
    void setRubrosConListaVaciaLosQuita() {
        Empresa empresa = new Empresa();
        empresa.setRubros(List.of(new Rubro(1L, "Agropecuario")));

        empresa.setRubros(List.of());

        assertTrue(empresa.getRubros().isEmpty());
    }
}
