package tse.lab.backofficeautoridadesadministradores.rest;

import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tse.lab.backofficeautoridadesadministradores.service.descargos.GestionPlazoDescargoService;
import tse.lab.backofficeautoridadesadministradores.service.descargos.PlazoDescargoException;
import tse.lab.backofficeautoridadesadministradores.service.descargos.GestionPlazoDescargoService.PlazoDescargo;

class PlazoDescargoResourceTest {
    private PlazoDescargoResource resource(boolean autenticado) throws Exception {
        var r=new PlazoDescargoResource();
        Field f=r.getClass().getDeclaredField("seguridad"); f.setAccessible(true);
        f.set(r,new SecurityContext() {
            public Principal getUserPrincipal() { return autenticado ? ()->"admin" : null; }
            public boolean isUserInRole(String role) { return true; }
            public boolean isSecure() { return false; }
            public String getAuthenticationScheme() { return "Bearer"; }
        });
        return r;
    }
    private PlazoDescargoResource.Cambio cambio(String dias) {
        return new PlazoDescargoResource.Cambio(BigDecimal.ONE,new BigDecimal(dias),"prueba");
    }
    @Test void rechazaFraccionesYDesbordesSinTruncarlos() throws Exception {
        var r=resource(true);
        assertEquals(400,r.actualizar(cambio("1.5")).getStatus());
        assertEquals(400,r.actualizar(cambio("999999999999999999999999")).getStatus());
        assertEquals(400,r.actualizar(null).getStatus());
    }
    @Test void requiereIdentidad() throws Exception {
        assertEquals(401,resource(false).obtener().getStatus());
        assertEquals(401,resource(false).actualizar(cambio("0")).getStatus());
    }
    @Test void conservaElConflictoDelServicio() throws Exception {
        var r=resource(true);
        Field f=r.getClass().getDeclaredField("servicio"); f.setAccessible(true);
        f.set(r,new GestionPlazoDescargoService() {
            @Override public PlazoDescargo actualizar(long base,int dias,String motivo,String correlation) {
                throw new PlazoDescargoException(409,"Revisión obsoleta");
            }
        });
        assertEquals(409,r.actualizar(cambio("0")).getStatus());
    }
}
