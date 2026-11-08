package tse.lab.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class ValidacionRutasTest {
    private static final String GEO_A = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.2,-33.2]]}";
    private static final String GEO_B = "{\"type\":\"LineString\",\"coordinates\":[[-58.0,-33.0],[-58.3,-33.3]]}";

    private static Guia guia() {
        return new Guia("G1", "Agro", 20000L, LocalDate.of(2026, 10, 31), "Mercedes", "Nueva Palmira", new Empresa());
    }

    private static RutaReferencia ruta(String geo) {
        return new RutaReferencia(geo, 40000);
    }

    @Test
    void aLocalityNeedsItsIdNameAndDepartment() {
        assertThrows(IllegalArgumentException.class, () -> new Localidad(null, "Mercedes", "Soriano", -33, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(0L, "Mercedes", "Soriano", -33, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, null, "Soriano", -33, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, " ", "Soriano", -33, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", null, -33, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", " ", -33, -58));
    }

    @Test
    void aLocalityNeedsCoordinatesInsideWgs84() {
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", "Soriano", -91, -58));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", "Soriano", 91, -58));
        assertThrows(IllegalArgumentException.class,
                () -> new Localidad(1L, "Mercedes", "Soriano", -33, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", "Soriano", -33, -181));
        assertThrows(IllegalArgumentException.class, () -> new Localidad(1L, "Mercedes", "Soriano", -33, 181));
    }

    @Test
    void aLocalityTrimsItsNames() {
        Localidad localidad = new Localidad(1L, " Mercedes ", " Soriano ", -33.2, -58.0);

        assertEquals("Mercedes", localidad.getNombre());
        assertEquals("Soriano", localidad.getDepartamento());
        assertEquals(-33.2, localidad.getLatitud());
        assertEquals(-58.0, localidad.getLongitud());
    }

    @Test
    void aRouteNeedsItsGeometryAndAPositiveDistance() {
        assertThrows(IllegalArgumentException.class, () -> new RutaReferencia(null, 12));
        assertThrows(IllegalArgumentException.class, () -> new RutaReferencia(GEO_A, 0));
        assertThrows(IllegalArgumentException.class, () -> new RutaReferencia(GEO_A, Double.POSITIVE_INFINITY));
        assertThrows(NullPointerException.class, () -> RutaReferencia.calcularHuella(null));

        RutaReferencia ruta = new RutaReferencia(GEO_A, 12);

        assertNull(ruta.getId());
        assertEquals(12, ruta.getDistanciaMetros());
        assertNotNull(ruta.getFechaCreacion());
    }

    @Test
    void anAlternativeNeedsItsGuideRouteAndAPositiveNumber() {
        Guia guia = guia();
        RutaReferencia ruta = ruta(GEO_A);

        assertThrows(NullPointerException.class, () -> new GuiaRutaAutorizada(null, ruta, 1));
        assertThrows(NullPointerException.class, () -> new GuiaRutaAutorizada(guia, null, 1));
        assertThrows(IllegalArgumentException.class, () -> new GuiaRutaAutorizada(guia, ruta, 0));
    }

    @Test
    void anAlternativeCannotBeReorderedToZero() {
        GuiaRutaAutorizada alternativa = new GuiaRutaAutorizada(guia(), ruta(GEO_A), 2);

        assertThrows(IllegalArgumentException.class, () -> alternativa.reordenar(0));
        alternativa.reordenar(1);

        assertEquals(1, alternativa.getNumeroAlternativa());
        assertNull(alternativa.getId());
        assertNotNull(alternativa.getGuia());
    }

    @Test
    void aGuideRequiresExactlyOneRoute() {
        Guia guia = guia();
        List<RutaReferencia> dos = List.of(ruta(GEO_A), ruta(GEO_B));

        assertThrows(IllegalArgumentException.class, () -> guia.reemplazarRutasAutorizadas(null));
        assertThrows(IllegalArgumentException.class, () -> guia.reemplazarRutasAutorizadas(List.of()));
        assertThrows(IllegalArgumentException.class, () -> guia.reemplazarRutasAutorizadas(dos));
        assertThrows(IllegalArgumentException.class,
                () -> guia.reemplazarRutasAutorizadas(Collections.singletonList(null)));
        assertTrue(guia.getRutasAutorizadas().isEmpty());
    }

    @Test
    void replacingKeepsOnlyTheNewRoute() {
        Guia guia = guia();
        RutaReferencia b = ruta(GEO_B);
        guia.reemplazarRutasAutorizadas(List.of(ruta(GEO_A)));

        guia.reemplazarRutasAutorizadas(List.of(b));

        assertEquals(List.of(GEO_B), guia.getRutasAutorizadas().stream()
                .map(r -> r.getRutaReferencia().getGeometriaGeoJson()).toList());
    }

    @Test
    void theSameTextDoesNotInvalidateTheRoutes() {
        Guia guia = guia();
        guia.reemplazarRutasAutorizadas(List.of(ruta(GEO_A)));

        guia.setOrigen("Mercedes");
        guia.setDestino("Nueva Palmira");

        assertEquals(1, guia.getRutasAutorizadas().size());
    }

    @Test
    void changingTheOriginDropsItsLocality() {
        Guia guia = guia();
        guia.setOrigenLocalidad(new Localidad(1L, "Mercedes", "Soriano", -33.2, -58.0));

        guia.setOrigen("Dolores");

        assertNull(guia.getOrigenLocalidad());
    }

    @Test
    void anotherDestinationLocalityInvalidatesTheRoutes() {
        Guia guia = guia();
        guia.setDestinoLocalidad(new Localidad(2L, "Nueva Palmira", "Colonia", -33.9, -58.4));
        guia.reemplazarRutasAutorizadas(List.of(ruta(GEO_A)));

        guia.setDestinoLocalidad(new Localidad(3L, "Nueva Palmira", "Otro", -34.0, -58.0));

        assertTrue(guia.getRutasAutorizadas().isEmpty());
        assertEquals(3L, guia.getDestinoLocalidad().getId());
    }

    @Test
    void aLocalityIsRequired() {
        Guia guia = guia();

        assertThrows(NullPointerException.class, () -> guia.setOrigenLocalidad(null));
        assertThrows(NullPointerException.class, () -> guia.setDestinoLocalidad(null));
    }
}
