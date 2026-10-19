package tse.lab.frontofficeciudadanos.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.rest.IngresoResource.IngresoRequest;
import tse.lab.frontofficeciudadanos.rest.IngresoResource.IngresoResponse;
import tse.lab.frontofficeciudadanos.rest.IngresoResource.IngresoTestingRequest;
import tse.lab.frontofficeciudadanos.service.adaptadorpdi.ResultadoValidacion;
import tse.lab.frontofficeciudadanos.service.autenticacion.IngresoService;
import tse.lab.frontofficeciudadanos.service.autenticacion.ResultadoIngreso;
import tse.lab.frontofficeciudadanos.service.autenticacion.RolPersona;
import tse.lab.model.Ciudadano;
import tse.lab.model.EstadoValidacion;

// CU-01: endpoints de ingreso (con gub.uy y de testing) con un IngresoService falso.
class IngresoResourceTest {

    private static final String CI = "54074380";
    private static final HttpServletRequest REQ = (HttpServletRequest) Proxy.newProxyInstance(
            IngresoResourceTest.class.getClassLoader(), new Class<?>[] { HttpServletRequest.class },
            (proxy, metodo, args) -> "getAttribute".equals(metodo.getName()) ? "corr-1" : null);

    private IngresoFalso ingreso;
    private IngresoResource recurso;

    @BeforeEach
    void setUp() throws Exception {
        ingreso = new IngresoFalso();
        recurso = new IngresoResource();
        Field f = IngresoResource.class.getDeclaredField("ingreso");
        f.setAccessible(true);
        f.set(recurso, ingreso);
    }

    @Test
    void ingresoDeTestingApagadoRespondeNotFound() {
        recurso.testingHabilitado = false;
        assertEquals(404, recurso.ingresoTesting(new IngresoTestingRequest(CI), REQ).getStatus());
        assertNull(ingreso.llamada);
    }

    @Test
    void ingresoDeTestingDevuelveElPerfilYLosRoles() {
        recurso.testingHabilitado = true;
        var res = recurso.ingresoTesting(new IngresoTestingRequest(CI), REQ);

        assertEquals(200, res.getStatus());
        IngresoResponse r = (IngresoResponse) res.getEntity();
        assertEquals("VALIDADO", r.resultado());
        assertEquals(CI, r.perfil().cedula());
        assertEquals(List.of(RolPersona.Rol.RESPONSABLE), r.roles().stream().map(RolPersona::rol).toList());
        assertEquals("testing " + CI + " corr-1", ingreso.llamada);
    }

    @Test
    void ingresoDeTestingConCedulaInvalida() {
        recurso.testingHabilitado = true;
        assertEquals(400, recurso.ingresoTesting(new IngresoTestingRequest("12"), REQ).getStatus());
        assertEquals(400, recurso.ingresoTesting(new IngresoTestingRequest(null), REQ).getStatus());
        assertEquals(400, recurso.ingresoTesting(null, REQ).getStatus());
        assertNull(ingreso.llamada);
    }

    @Test
    void ingresoConGubUy() {
        var res = recurso.ingresoGubUy(new IngresoRequest(CI, "r@prueba.uy", "sub-1"), REQ);

        assertEquals(200, res.getStatus());
        assertEquals(CI, ((IngresoResponse) res.getEntity()).perfil().cedula());
        assertEquals("gubuy " + CI + " sub-1 corr-1", ingreso.llamada);
    }

    @Test
    void ingresoConGubUySinSubEsInvalido() {
        assertEquals(400, recurso.ingresoGubUy(new IngresoRequest(CI, null, " "), REQ).getStatus());
        assertEquals(400, recurso.ingresoGubUy(new IngresoRequest("abc", null, "sub-1"), REQ).getStatus());
        assertEquals(400, recurso.ingresoGubUy(null, REQ).getStatus());
        assertNull(ingreso.llamada);
    }

    private static class IngresoFalso extends IngresoService {
        String llamada;

        @Override
        public ResultadoIngreso ingresarConGubUy(String cedula, String email, String sub, String correlationId) {
            llamada = "gubuy " + cedula + " " + sub + " " + correlationId;
            return resultado(cedula);
        }

        @Override
        public ResultadoIngreso ingresarParaTesting(String cedula, String correlationId) {
            llamada = "testing " + cedula + " " + correlationId;
            return resultado(cedula);
        }

        private static ResultadoIngreso resultado(String cedula) {
            Ciudadano p = new Ciudadano(cedula, "r@prueba.uy", null);
            p.setEstado(EstadoValidacion.VALIDADO);
            return new ResultadoIngreso(p, ResultadoValidacion.VALIDADO, false, true,
                    List.of(new RolPersona(RolPersona.Rol.RESPONSABLE, 1L, "Transportes del Este", null)));
        }
    }
}
