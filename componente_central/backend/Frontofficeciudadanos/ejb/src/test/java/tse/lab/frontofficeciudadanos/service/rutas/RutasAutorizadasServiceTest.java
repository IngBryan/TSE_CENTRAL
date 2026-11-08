package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.model.RutaReferencia;
import tse.lab.model.Guia;
import tse.lab.model.GuiaRutaAutorizada;

/** La evidencia se recupera por SHA, no por enlaces de guia que pueden cambiar. */
class RutasAutorizadasServiceTest {
    @Test
    void consultaActualSoloAdmiteLaPrincipalDeGuiasAnteriores() throws Exception {
        RutaReferencia a = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}", 5000);
        RutaReferencia b = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[1,1],[2,2]]}", 6000);
        RutaReferenciaDAO dao = mock(RutaReferenciaDAO.class);
        when(dao.buscarPorGuia("G1")).thenReturn(List.of(
                new GuiaRutaAutorizada(new Guia(), a, 1), new GuiaRutaAutorizada(new Guia(), b, 2)));
        var resultado = servicio(dao).obtenerPorGuia("G1");
        assertEquals(1, resultado.size());
        assertEquals(a.getSha256GeoJson(), resultado.getFirst().getSha256());
    }

    private static RutasAutorizadasService servicio(RutaReferenciaDAO dao) throws Exception {
        RutasAutorizadasService servicio = new RutasAutorizadasService();
        Field f = RutasAutorizadasService.class.getDeclaredField("rutas");
        f.setAccessible(true);
        f.set(servicio, dao);
        return servicio;
    }

    @Test
    void recuperaReferenciasEnOrdenHistoricoSinConsultarGuia() throws Exception {
        RutaReferencia a = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}", 5000);
        RutaReferencia b = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[1,1],[2,2]]}", 6000);
        RutaReferenciaDAO dao = mock(RutaReferenciaDAO.class);
        List<String> hashes = List.of(b.getSha256GeoJson(), a.getSha256GeoJson());
        when(dao.buscarPorHuellas(hashes)).thenReturn(List.of(a, b));
        List<DatosRutaAutorizada> resultado = servicio(dao).obtenerPorHuellas(hashes);
        assertEquals(hashes, resultado.stream().map(DatosRutaAutorizada::getSha256).toList());
        assertEquals(List.of(1, 2), resultado.stream().map(DatosRutaAutorizada::getAlternativa).toList());
        verify(dao, never()).buscarPorGuia(anyString());
    }

    @Test
    void descartaRutaInexistenteYNoInventarGeometria() throws Exception {
        RutaReferenciaDAO dao = mock(RutaReferenciaDAO.class);
        String hash = "a".repeat(64);
        when(dao.buscarPorHuellas(List.of(hash))).thenReturn(List.of());
        assertTrue(servicio(dao).obtenerPorHuellas(List.of(hash)).isEmpty());
        assertTrue(servicio(dao).obtenerPorHuellas(List.of("hash-invalido")).isEmpty());
        verify(dao, never()).buscarPorGuia(anyString());
    }
}
