package tse.lab.frontofficeciudadanos.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.gestionchoferes.AltaChoferService;

class ChoferesResourceTest {
    private static final String CI = "54074380";
    private static final HttpServletRequest REQ = (HttpServletRequest) Proxy.newProxyInstance(
            ChoferesResourceTest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
            (proxy, metodo, args) -> "getAttribute".equals(metodo.getName()) ? "corr-1" : null);

    // La baja responde lo que se le indique: nada (baja hecha) o la excepcion
    private static class AltaFalsa extends AltaChoferService {
        Exception fallo;
        String baja;

        @Override
        public void darDeBaja(String ciResponsable, String ciChofer, String correlationId)
                throws NoAutorizadoException, RecursoNoEncontradoException, ConflictoException {
            if (fallo instanceof NoAutorizadoException e) {
                throw e;
            }
            if (fallo instanceof RecursoNoEncontradoException e) {
                throw e;
            }
            if (fallo instanceof ConflictoException e) {
                throw e;
            }
            baja = ciResponsable + ":" + ciChofer + ":" + correlationId;
        }
    }

    private final AltaFalsa alta = new AltaFalsa();

    private ChoferesResource recurso() throws Exception {
        ChoferesResource recurso = new ChoferesResource();
        Field f = ChoferesResource.class.getDeclaredField("altaChofer");
        f.setAccessible(true);
        f.set(recurso, alta);
        return recurso;
    }

    @Test
    void darDeBajaRespondeSinContenidoYPasaQuienLlamaYElCorrelationId() throws Exception {
        Response r = recurso().darDeBaja(CI, "22222222", REQ);

        assertEquals(204, r.getStatus());
        assertEquals(CI + ":22222222:corr-1", alta.baja);
    }

    @Test
    void unaCedulaInvalidaNoLlegaAlServicio() throws Exception {
        assertEquals(400, recurso().darDeBaja(CI, "12a", REQ).getStatus());
        assertEquals(400, recurso().darDeBaja(CI, null, REQ).getStatus());
        assertEquals(null, alta.baja);
    }

    @Test
    void siNoEsResponsableResponde403() throws Exception {
        alta.fallo = new NoAutorizadoException("no es responsable");

        Response r = recurso().darDeBaja(CI, "22222222", REQ);

        assertEquals(403, r.getStatus());
        assertEquals("no_autorizado", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void siNoEsChoferDeLaEmpresaResponde404() throws Exception {
        alta.fallo = new RecursoNoEncontradoException("La persona no es chofer de la empresa.");

        Response r = recurso().darDeBaja(CI, "22222222", REQ);

        assertEquals(404, r.getStatus());
        assertEquals("no_encontrado", ((Map<?, ?>) r.getEntity()).get("error"));
    }

    @Test
    void siTieneUnViajeActivoResponde409ConElCodigo() throws Exception {
        alta.fallo = new ConflictoException("CHOFER_CON_VIAJE", "El chofer tiene un viaje asignado o en curso.");

        Response r = recurso().darDeBaja(CI, "56087406", REQ);

        assertEquals(409, r.getStatus());
        Map<?, ?> cuerpo = (Map<?, ?>) r.getEntity();
        assertEquals("CHOFER_CON_VIAJE", cuerpo.get("error"));
        assertEquals("El chofer tiene un viaje asignado o en curso.", cuerpo.get("detalle"));
    }
}
