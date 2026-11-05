package tse.lab.model;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RutasCompartidasTest {
    private static final String GEO_A = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.2,-33.2]]}";
    private static final String GEO_B = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.3,-33.3]]}";

    private static Guia guia(String id) {
        return new Guia(id, "Agro", 20000L, LocalDate.of(2026, 10, 31),
                "Mercedes", "Nueva Palmira", new Empresa());
    }

    @Test
    void dosGuiasPuedenCompartirExactamenteLaMismaGeometria() {
        RutaReferencia ruta = new RutaReferencia(GEO_A, 40000);
        Guia primera = guia("G1");
        Guia segunda = guia("G2");
        primera.reemplazarRutasAutorizadas(List.of(ruta));
        segunda.reemplazarRutasAutorizadas(List.of(ruta));
        assertSame(ruta, primera.getRutasAutorizadas().get(0).getRutaReferencia());
        assertSame(ruta, segunda.getRutasAutorizadas().get(0).getRutaReferencia());
        assertEquals(64, ruta.getSha256GeoJson().length());
        assertEquals(ruta.getSha256GeoJson(), RutaReferencia.calcularHuella(GEO_A));
        assertNotEquals(ruta.getSha256GeoJson(), RutaReferencia.calcularHuella(GEO_B));
    }

    @Test
    void lasAsociacionesSeBloqueanCuandoElViajeComenzo() {
        Guia guia = guia("G3");
        guia.reemplazarRutasAutorizadas(List.of(new RutaReferencia(GEO_A, 40000)));
        guia.cambiarEstado(EstadoGuia.EN_CURSO);
        assertThrows(IllegalStateException.class,
                () -> guia.reemplazarRutasAutorizadas(List.of(new RutaReferencia(GEO_B, 50000))));
        assertEquals(1, guia.getRutasAutorizadas().size());
    }

    @Test
    void lasAlternativasSonDistintasYSePuedenReordenarAntesDeIniciar() {
        Guia guia = guia("G4");
        RutaReferencia rutaA = new RutaReferencia(GEO_A, 40000);
        RutaReferencia rutaB = new RutaReferencia(GEO_B, 50000);
        guia.reemplazarRutasAutorizadas(List.of(rutaA, rutaB));
        assertThrows(IllegalArgumentException.class,
                () -> guia.reemplazarRutasAutorizadas(List.of(rutaA, rutaA)));
        guia.reemplazarRutasAutorizadas(List.of(rutaB, rutaA));
        assertSame(rutaB, guia.getRutasAutorizadas().get(0).getRutaReferencia());
        assertEquals(1, guia.getRutasAutorizadas().get(0).getNumeroAlternativa());
        assertThrows(UnsupportedOperationException.class,
                () -> guia.getRutasAutorizadas().clear());
    }

    @Test
    void validaGeometriasYLocalidades() {
        assertThrows(IllegalArgumentException.class, () -> new RutaReferencia("", 12));
        assertThrows(IllegalArgumentException.class, () -> new RutaReferencia(GEO_A, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new Localidad(10L, "Mercedes", "Soriano", Double.NaN, -58.0));
        Localidad localidad = new Localidad(10L, "Mercedes", "Soriano", -33.2, -58.0);
        assertEquals(Long.valueOf(10L), localidad.getId());
        assertEquals("Soriano", localidad.getDepartamento());
    }

    @Test
    void cambioDeLocalidadesInvalidaAsociacionesSinEliminarGeometriaCompartida() {
        RutaReferencia referencia = new RutaReferencia(GEO_A, 40000);
        Guia guia = guia("G5");
        guia.reemplazarRutasAutorizadas(List.of(referencia));
        guia.setDestino("Dolores");
        assertTrue(guia.getRutasAutorizadas().isEmpty());
        assertEquals(GEO_A, referencia.getGeometriaGeoJson());
    }

    @Test
    void vincularLocalidadesHistoricasConNombreIdenticoConservaLasRutas() {
        Guia guia = guia("G-HIST");
        RutaReferencia ruta = new RutaReferencia(GEO_A, 40000);
        guia.reemplazarRutasAutorizadas(List.of(ruta));
        guia.setOrigenLocalidad(new Localidad(1L, "Mercedes", "Soriano", -33.2, -58.0));
        guia.setDestinoLocalidad(new Localidad(2L, "Nueva Palmira", "Colonia", -33.9, -58.4));
        assertEquals(1, guia.getRutasAutorizadas().size());
        assertEquals(1L, guia.getOrigenLocalidad().getId());
        assertEquals(2L, guia.getDestinoLocalidad().getId());
    }

    @Test
    void cambiarIdentidadDeLocalidadInvalidaRutasAunqueElNombreCoincida() {
        Guia guia = guia("G-IDS");
        guia.setOrigenLocalidad(new Localidad(5L, "Mercedes", "Soriano", -33.2, -58.0));
        guia.reemplazarRutasAutorizadas(List.of(new RutaReferencia(GEO_A, 40000)));
        guia.setOrigenLocalidad(new Localidad(6L, "Mercedes", "Otro", -34.0, -57.0));
        assertTrue(guia.getRutasAutorizadas().isEmpty());
        assertEquals(6L, guia.getOrigenLocalidad().getId());
    }

    @Test
    void noPermiteCambiarOrigenConRutasFijadasTrasInicio() {
        Guia guia = guia("G6");
        guia.reemplazarRutasAutorizadas(List.of(new RutaReferencia(GEO_A, 40000)));
        guia.cambiarEstado(EstadoGuia.EN_CURSO);
        assertThrows(IllegalStateException.class, () -> guia.setOrigen("Dolores"));
        assertEquals("Mercedes", guia.getOrigen());
        assertEquals(1, guia.getRutasAutorizadas().size());
    }

}
