package tse.lab.frontofficeciudadanos.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.rest.GuiasRutasResource.Confirmacion;
import tse.lab.frontofficeciudadanos.rest.LocalidadesResource.LocalidadVista;
import tse.lab.frontofficeciudadanos.service.rutas.DatosRutaAutorizada;
import tse.lab.frontofficeciudadanos.service.rutas.PrevisualizacionRutasService;
import tse.lab.frontofficeciudadanos.service.rutas.PrevisualizacionRutasService.Previsualizacion;
import tse.lab.frontofficeciudadanos.service.rutas.RutasException;
import tse.lab.frontofficeciudadanos.service.rutas.RutasGuiaService;
import tse.lab.model.Localidad;

class RutasResourcesTest {
    private static final String CI = "12345678";
    private static final HttpServletRequest REQ = (HttpServletRequest) Proxy.newProxyInstance(
            RutasResourcesTest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
            (proxy, metodo, args) -> "getAttribute".equals(metodo.getName()) ? "corr-1" : null);
    private static final List<DatosRutaAutorizada> RUTAS = List.of(new DatosRutaAutorizada(7L, 1, "sha", 100, "geo"));

    private static class RutasFalsas extends RutasGuiaService {
        RuntimeException fallo;
        String confirmado;

        private List<DatosRutaAutorizada> responder() {
            if (fallo != null) {
                throw fallo;
            }
            return RUTAS;
        }

        @Override
        public List<DatosRutaAutorizada> consultar(String ciResponsable, String idGuia, String correlationId) {
            return responder();
        }

        @Override
        public List<DatosRutaAutorizada> confirmar(String ciResponsable, String idGuia, Long origenId, Long destinoId,
                String previsualizacionId, String correlationId) {
            confirmado = ciResponsable + ":" + idGuia + ":" + origenId + ":" + destinoId + ":" + previsualizacionId
                    + ":" + correlationId;
            return responder();
        }
    }

    private final RutasFalsas rutas = new RutasFalsas();

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }

    private GuiasRutasResource recurso() throws Exception {
        GuiasRutasResource recurso = new GuiasRutasResource();
        set(recurso, "rutas", rutas);
        return recurso;
    }

    @Test
    void returnsTheRoutesOfTheGuide() throws Exception {
        Response r = recurso().consultar(CI, "G1", REQ);

        assertEquals(200, r.getStatus());
        assertSame(RUTAS, r.getEntity());
    }

    @Test
    void confirmsWithTheDataOfTheBody() throws Exception {
        Response r = recurso().confirmar(CI, "G1", new Confirmacion(1L, 2L, "previa"), REQ);

        assertEquals(200, r.getStatus());
        assertEquals(CI + ":G1:1:2:previa:corr-1", rutas.confirmado);
    }

    @Test
    void aBodyIsRequired() throws Exception {
        Response r = recurso().confirmar(CI, "G1", null, REQ);

        assertEquals(400, r.getStatus());
        assertEquals(Map.of("error", "solicitud_invalida"), r.getEntity());
    }

    @Test
    void aRoutesErrorKeepsItsStatusAndCode() throws Exception {
        rutas.fallo = new RutasException(409, "rutas_actualizadas", "Cambiaron");

        Response r = recurso().consultar(CI, "G1", REQ);

        assertEquals(409, r.getStatus());
        assertEquals(Map.of("error", "rutas_actualizadas", "detalle", "Cambiaron"), r.getEntity());
    }

    @Test
    void aTripThatCannotChangeIsAConflict() throws Exception {
        rutas.fallo = new IllegalStateException("Viaje iniciado");

        Response r = recurso().confirmar(CI, "G1", new Confirmacion(1L, 2L, "previa"), REQ);

        assertEquals(409, r.getStatus());
        assertEquals(Map.of("error", "viaje_no_modificable", "detalle", "Viaje iniciado"), r.getEntity());
    }

    @Test
    void listsTheLocalities() throws Exception {
        LocalidadesResource recurso = new LocalidadesResource();
        set(recurso, "localidades", new LocalidadDAO() {
            @Override
            public List<Localidad> listar() {
                return List.of(new Localidad(1L, "Mercedes", "Soriano", -33.25, -58.03));
            }
        });

        assertEquals(List.of(new LocalidadVista(1L, "Mercedes", "Soriano")), recurso.listar());
    }

    private static RutasPrevisualizacionResource previsualizacion(PrevisualizacionRutasService servicio)
            throws Exception {
        RutasPrevisualizacionResource recurso = new RutasPrevisualizacionResource();
        set(recurso, "rutas", servicio);
        return recurso;
    }

    @Test
    void previewsTheRoutes() throws Exception {
        Previsualizacion previa = new Previsualizacion(1L, 2L, "id", List.of());

        Response r = previsualizacion(new PrevisualizacionRutasService() {
            @Override
            public Previsualizacion calcular(Long origenId, Long destinoId) {
                return previa;
            }
        }).previsualizar(1L, 2L);

        assertEquals(200, r.getStatus());
        assertSame(previa, r.getEntity());
    }

    @Test
    void aPreviewErrorKeepsItsStatusAndCode() throws Exception {
        Response r = previsualizacion(new PrevisualizacionRutasService() {
            @Override
            public Previsualizacion calcular(Long origenId, Long destinoId) {
                throw new RutasException(422, "sin_ruta_vial", "Sin ruta");
            }
        }).previsualizar(1L, 2L);

        assertEquals(422, r.getStatus());
        assertEquals(Map.of("error", "sin_ruta_vial", "detalle", "Sin ruta"), r.getEntity());
    }
}
