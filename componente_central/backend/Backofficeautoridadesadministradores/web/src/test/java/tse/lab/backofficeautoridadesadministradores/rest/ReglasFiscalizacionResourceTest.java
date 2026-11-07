package tse.lab.backofficeautoridadesadministradores.rest;

import jakarta.ws.rs.core.SecurityContext;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.security.Principal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tse.lab.backofficeautoridadesadministradores.service.reglas.GestionReglasService;
import tse.lab.backofficeautoridadesadministradores.service.reglas.ReglasException;
import tse.lab.reglas.ConfiguracionReglas;

class ReglasFiscalizacionResourceTest {
    private ReglasFiscalizacionResource resource(boolean autenticado) throws Exception {
        var r=new ReglasFiscalizacionResource();
        Field f=r.getClass().getDeclaredField("seguridad"); f.setAccessible(true);
        f.set(r,new SecurityContext() {
            public Principal getUserPrincipal() { return autenticado ? ()->"admin" : null; }
            public boolean isUserInRole(String role) { return true; }
            public boolean isSecure() { return false; }
            public String getAuthenticationScheme() { return "Bearer"; }
        });
        return r;
    }
    private ReglasFiscalizacionResource.Cambio cambio(String kg) {
        return new ReglasFiscalizacionResource.Cambio(BigDecimal.ONE,BigDecimal.ZERO,new BigDecimal(kg),
                BigDecimal.valueOf(2000),BigDecimal.valueOf(30),"prueba");
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
        f.set(r,new GestionReglasService() {
            @Override public ConfiguracionReglas actualizar(long base,BigDecimal p,int kg,int m,int s,String motivo,String correlation) {
                throw new ReglasException(409,"Revisión obsoleta");
            }
        });
        assertEquals(409,r.actualizar(cambio("0")).getStatus());
    }
}
