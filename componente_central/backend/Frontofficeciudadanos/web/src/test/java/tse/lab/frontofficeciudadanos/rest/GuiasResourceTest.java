package tse.lab.frontofficeciudadanos.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.EdicionGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.GuiaVista;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.service.rutas.RutasException;
import tse.lab.frontofficeciudadanos.service.rutas.RutasGuiaService;

class GuiasResourceTest {
    private static final String CI = "12345678";
    private static final String PREVIA = "a".repeat(64);
    private static final GuiaVista GUIA = new GuiaVista("G1", "Forestal", "Rollizos", 1000,
            "2026-11-08", "Origen", "Destino", "SIN_ASIGNAR", null, null, 0L, 1L, 2L, "A", "B");
    private static final HttpServletRequest REQ = (HttpServletRequest) Proxy.newProxyInstance(
            GuiasResourceTest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
            (proxy, metodo, args) -> "getAttribute".equals(metodo.getName()) ? "corr-1" : null);

    private record Invocacion(String usuario, String idGuia, Object datos, String correlationId) { }

    private static class RutasSimuladas extends RutasGuiaService {
        Invocacion llamada;
        RuntimeException fallo;

        private GuiaVista responder(String usuario, String idGuia, Object datos, String correlationId) {
            llamada = new Invocacion(usuario, idGuia, datos, correlationId);
            if (fallo != null) throw fallo;
            return GUIA;
        }

        @Override
        public GuiaVista crearGuia(String usuario, NuevaGuia datos, String correlationId) {
            return responder(usuario, null, datos, correlationId);
        }

        @Override
        public GuiaVista editarGuia(String usuario, String idGuia, EdicionGuia datos, String correlationId) {
            return responder(usuario, idGuia, datos, correlationId);
        }
    }

    private static class GuiasSimuladas extends GuiasService {
        Invocacion llamada;

        @Override
        public GuiaVista asignar(String usuario, String idGuia, String matricula, Long idChofer,
                String correlationId) {
            llamada = new Invocacion(usuario, idGuia,
                    new GuiasResource.AsignacionRequest(matricula, idChofer), correlationId);
            return GUIA;
        }
    }

    private final GuiasResource recurso = new GuiasResource();
    private final RutasSimuladas rutas = new RutasSimuladas();
    private final GuiasSimuladas guias = new GuiasSimuladas();

    @BeforeEach
    void preparar() throws Exception {
        inyectar("rutas", rutas);
        inyectar("guias", guias);
    }

    @Test
    void elAltaEntregaLaPrevisualizacionYElUsuarioAlServicioDeRutas() {
        var datos = new NuevaGuia("Forestal", "Rollizos", 1000L, "2026-11-08",
                null, null, 1L, 2L, PREVIA);

        Response respuesta = recurso.crear(CI, datos, REQ);

        assertEquals(200, respuesta.getStatus());
        assertSame(GUIA, respuesta.getEntity());
        assertEquals(new Invocacion(CI, null, datos, "corr-1"), rutas.llamada);
        assertNull(guias.llamada);
    }

    @Test
    void editarSinPrevisualizacionSeDelegaConSuVersionYContexto() {
        var datos = new EdicionGuia("Forestal", "Rollizos", 2000L, "2026-11-08",
                null, null, 1L, 2L, 3L, null);

        Response respuesta = recurso.editar(CI, "G1", datos, REQ);

        assertEquals(200, respuesta.getStatus());
        assertSame(GUIA, respuesta.getEntity());
        assertEquals(new Invocacion(CI, "G1", datos, "corr-1"), rutas.llamada);
        assertNull(guias.llamada);
    }

    @Test
    void asignarVehiculoYChoferNoVuelveAConfirmarLaRuta() {
        var datos = new GuiasResource.AsignacionRequest("SBC1234", 5L);

        Response respuesta = recurso.asignar(CI, "G1", datos, REQ);

        assertEquals(200, respuesta.getStatus());
        assertSame(GUIA, respuesta.getEntity());
        assertEquals(new Invocacion(CI, "G1", datos, "corr-1"), guias.llamada);
        assertNull(rutas.llamada);
    }

    @Test
    void unErrorDeRutaConservaElEstadoYElCodigoEnAltaYEdicion() {
        rutas.fallo = new RutasException(409, "rutas_actualizadas", "Previsualizá nuevamente");

        for (Response respuesta : new Response[] {
                recurso.crear(CI, null, REQ), recurso.editar(CI, "G1", null, REQ) }) {
            assertEquals(409, respuesta.getStatus());
            assertEquals(Map.of("error", "rutas_actualizadas", "detalle", "Previsualizá nuevamente"),
                    respuesta.getEntity());
        }
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = GuiasResource.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(recurso, valor);
    }
}
