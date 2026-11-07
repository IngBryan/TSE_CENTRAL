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

import tse.lab.backofficeautoridadesadministradores.rest.UsuariosResource.AltaUsuarioRequest;
import tse.lab.backofficeautoridadesadministradores.rest.UsuariosResource.CambioRolRequest;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolNoPermitidoException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.CambioRolService.RolCambiado;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioNoEncontradoException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.AltaUsuarioService.UsuarioCreado;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.DatosUsuario;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.DatosUsuarioInvalidosException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.UsuarioExistenteException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.GestionUsuariosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaInvalidaException;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.PaginaUsuarios;
import tse.lab.backofficeautoridadesadministradores.service.gestionusuariosyroles.RolUsuario;

class UsuariosResourceTest {

    private static final AltaUsuarioRequest ALTA_ADMINISTRADOR =
            new AltaUsuarioRequest("ADMINISTRADOR", "1234567", "Clave-segura-2026", null);

    private static final CambioRolRequest A_AUTORIDAD = new CambioRolRequest("AUTORIDAD", null, null);

    private UsuariosResource recurso;
    private GestionUsuariosService servicio;
    private AltaUsuarioService alta;
    private CambioRolService cambioRol;
    private SecurityContext seguridad;

    @BeforeEach
    void preparar() throws Exception {
        recurso = new UsuariosResource();
        servicio = mock(GestionUsuariosService.class);
        alta = mock(AltaUsuarioService.class);
        cambioRol = mock(CambioRolService.class);
        seguridad = mock(SecurityContext.class);

        inyectar("servicio", servicio);
        inyectar("alta", alta);
        inyectar("cambioRol", cambioRol);
        inyectar("seguridad", seguridad);
    }

    private Map<?, ?> cuerpo(Response respuesta) {
        return (Map<?, ?>) respuesta.getEntity();
    }

    @Test
    void unAdministradorCambiaElRol() {
        RolCambiado cambiado = new RolCambiado("1234567", RolUsuario.AUTORIDAD);
        autenticarAdministrador();
        when(cambioRol.cambiar("99999999", "1234567", "AUTORIDAD", null, null)).thenReturn(cambiado);

        Response respuesta = recurso.cambiarRol("1234567", A_AUTORIDAD);

        assertEquals(200, respuesta.getStatus());
        assertSame(cambiado, respuesta.getEntity());
    }

    @Test
    void cambioDeRolConDatosInvalidosDevuelve400() {
        autenticarAdministrador();
        when(cambioRol.cambiar("99999999", "1234567", "AUTORIDAD", null, null))
                .thenThrow(new DatosUsuarioInvalidosException("Falta la contraseña"));

        Response respuesta = recurso.cambiarRol("1234567", A_AUTORIDAD);

        assertEquals(400, respuesta.getStatus());
        assertEquals("Falta la contraseña", cuerpo(respuesta).get("detalle"));
    }

    @Test
    void cambioDeRolDeQuienNoEsUsuarioInternoDevuelve404() {
        autenticarAdministrador();
        when(cambioRol.cambiar("99999999", "1234567", "AUTORIDAD", null, null))
                .thenThrow(new UsuarioNoEncontradoException("No es"));

        Response respuesta = recurso.cambiarRol("1234567", A_AUTORIDAD);

        assertEquals(404, respuesta.getStatus());
        assertEquals("usuario_no_encontrado", cuerpo(respuesta).get("error"));
    }

    @Test
    void cambioDeRolNoPermitidoDevuelve409() {
        autenticarAdministrador();
        when(cambioRol.cambiar("99999999", "1234567", "AUTORIDAD", null, null))
                .thenThrow(new CambioRolNoPermitidoException("Ya tiene ese rol"));

        Response respuesta = recurso.cambiarRol("1234567", A_AUTORIDAD);

        assertEquals(409, respuesta.getStatus());
        assertEquals("cambio_no_permitido", cuerpo(respuesta).get("error"));
    }

    @Test
    void cambioDeRolSinCuerpoDevuelve400() {
        autenticarAdministrador();

        assertEquals(400, recurso.cambiarRol("1234567", null).getStatus());
        verifyNoInteractions(cambioRol);
    }

    @Test
    void unaAutoridadNoPuedeCambiarRoles() {
        when(seguridad.getUserPrincipal()).thenReturn(() -> "88888888");
        when(seguridad.isUserInRole("AUTORIDAD")).thenReturn(true);

        assertEquals(403, recurso.cambiarRol("1234567", A_AUTORIDAD).getStatus());
        assertEquals(401, new UsuariosResource().cambiarRol("1234567", A_AUTORIDAD).getStatus());
        verifyNoInteractions(cambioRol);
    }

    @Test
    void unAdministradorCreaUnUsuario() {
        UsuarioCreado creado = new UsuarioCreado("1234567", RolUsuario.ADMINISTRADOR);
        autenticarAdministrador();
        when(alta.crear("99999999", "ADMINISTRADOR", "1234567", "Clave-segura-2026", null))
                .thenReturn(creado);

        Response respuesta = recurso.crear(ALTA_ADMINISTRADOR);

        assertEquals(201, respuesta.getStatus());
        assertSame(creado, respuesta.getEntity());
    }

    @Test
    void altaConDatosInvalidosDevuelve400() {
        autenticarAdministrador();
        when(alta.crear("99999999", "ADMINISTRADOR", "1234567", "Clave-segura-2026", null))
                .thenThrow(new DatosUsuarioInvalidosException("La contraseña es corta"));

        Response respuesta = recurso.crear(ALTA_ADMINISTRADOR);

        assertEquals(400, respuesta.getStatus());
        assertEquals("datos_invalidos", cuerpo(respuesta).get("error"));
        assertEquals("La contraseña es corta", cuerpo(respuesta).get("detalle"));
    }

    @Test
    void altaSinCuerpoDevuelve400() {
        autenticarAdministrador();

        Response respuesta = recurso.crear(null);

        assertEquals(400, respuesta.getStatus());
        verifyNoInteractions(alta);
    }

    @Test
    void altaDeUnUsuarioExistenteDevuelve409() {
        autenticarAdministrador();
        when(alta.crear("99999999", "ADMINISTRADOR", "1234567", "Clave-segura-2026", null))
                .thenThrow(new UsuarioExistenteException("Ya existe"));

        Response respuesta = recurso.crear(ALTA_ADMINISTRADOR);

        assertEquals(409, respuesta.getStatus());
        assertEquals("usuario_existente", cuerpo(respuesta).get("error"));
    }

    @Test
    void unaAutoridadNoPuedeCrearUsuarios() {
        when(seguridad.getUserPrincipal()).thenReturn(() -> "88888888");
        when(seguridad.isUserInRole("AUTORIDAD")).thenReturn(true);

        assertEquals(403, recurso.crear(ALTA_ADMINISTRADOR).getStatus());
        verifyNoInteractions(alta);
    }

    @Test
    void sinAutenticarNoSeCreanUsuarios() {
        assertEquals(401, recurso.crear(ALTA_ADMINISTRADOR).getStatus());
        verifyNoInteractions(alta);
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
