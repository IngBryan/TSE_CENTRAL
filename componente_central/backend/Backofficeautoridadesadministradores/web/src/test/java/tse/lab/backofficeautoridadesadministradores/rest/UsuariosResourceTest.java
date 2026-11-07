package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.DatosUsuario;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.GestionUsuariosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaInvalidaException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaUsuarios;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.RolUsuario;

class UsuariosResourceTest {

    private UsuariosResource recurso;
    private GestionUsuariosService servicio;
    private SecurityContext seguridad;

    @BeforeEach
    void preparar() throws Exception {
        recurso = new UsuariosResource();
        servicio = mock(GestionUsuariosService.class);
        seguridad = mock(SecurityContext.class);

        inyectar("servicio", servicio);
        inyectar("seguridad", seguridad);
    }

    private void inyectar(String campo, Object valor) throws Exception {
        Field field = UsuariosResource.class.getDeclaredField(campo);
        field.setAccessible(true);
        field.set(recurso, valor);
    }

    private void autenticarAdministrador() {
        when(seguridad.getUserPrincipal()).thenReturn(() -> "99999999");
        when(seguridad.isUserInRole("ADMINISTRADOR")).thenReturn(true);
    }

    @Test
    void unAdministradorObtieneLaPaginaPedida() {
        PaginaUsuarios pagina = new PaginaUsuarios(
                List.of(new DatosUsuario(
                        "11111111", "Ana", "Perez", "ana@example.com", List.of(RolUsuario.CHOFER))),
                2, 10, 11, 2);
        autenticarAdministrador();
        when(servicio.listar("2", "10", "CHOFER", "1")).thenReturn(pagina);

        Response respuesta = recurso.listar("2", "10", "CHOFER", "1");

        assertEquals(200, respuesta.getStatus());
        assertSame(pagina, respuesta.getEntity());
    }

    @Test
    void parametrosInvalidosDevuelven400() {
        autenticarAdministrador();
        when(servicio.listar("0", null, null, null))
                .thenThrow(new PaginaInvalidaException("La pagina no es valida"));

        Response respuesta = recurso.listar("0", null, null, null);

        assertEquals(400, respuesta.getStatus());
        Map<?, ?> error = (Map<?, ?>) respuesta.getEntity();
        assertEquals("datos_invalidos", error.get("error"));
        assertEquals("La pagina no es valida", error.get("detalle"));
    }

    @Test
    void sinAutenticarDevuelve401() {
        Response respuesta = recurso.listar(null, null, null, null);

        assertEquals(401, respuesta.getStatus());
        assertEquals("no_autenticado", ((Map<?, ?>) respuesta.getEntity()).get("error"));
        verifyNoInteractions(servicio);
    }

    @Test
    void unaAutoridadRecibe403() {
        when(seguridad.getUserPrincipal()).thenReturn(() -> "88888888");
        when(seguridad.isUserInRole("AUTORIDAD")).thenReturn(true);

        Response respuesta = recurso.listar(null, null, null, null);

        assertEquals(403, respuesta.getStatus());
        assertEquals("no_autorizado", ((Map<?, ?>) respuesta.getEntity()).get("error"));
        verifyNoInteractions(servicio);
    }
}
