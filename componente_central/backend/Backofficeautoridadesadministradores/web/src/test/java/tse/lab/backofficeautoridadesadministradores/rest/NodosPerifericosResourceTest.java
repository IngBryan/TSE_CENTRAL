
package tse.lab.backofficeautoridadesadministradores.rest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodo;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.DatosNodoInvalidosException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.GestionNodosPerifericosService;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.NodoDuplicadoException;
import tse.lab.backofficeautoridadesadministradores.service.gestionnodosperifericos.TipoNodo;

class NodosPerifericosResourceTest {

    private NodosPerifericosResource recurso;
    private GestionNodosPerifericosService servicio;
    private SecurityContext seguridad;

    @BeforeEach
    void preparar() throws Exception {
        recurso = new NodosPerifericosResource();
        servicio = mock(GestionNodosPerifericosService.class);
        seguridad = mock(SecurityContext.class);

        inyectar("servicio", servicio);
        inyectar("seguridad", seguridad);

        when(seguridad.getUserPrincipal())
                .thenReturn(() -> "99999999");

        when(seguridad.isUserInRole("ADMINISTRADOR"))
                .thenReturn(true);
    }

    private void inyectar(String campo, Object valor)
            throws Exception {
        Field field = NodosPerifericosResource.class
                .getDeclaredField(campo);

        field.setAccessible(true);
        field.set(recurso, valor);
    }

    private DatosNodo nodoEjemplo() {
        return new DatosNodo(
                1L,
                9001L,
                "Balanza Ruta 5",
                TipoNodo.BALANZA,
                "Ruta 5 km 100",
                true,
                null
        );
    }

    private NodosPerifericosResource.AltaNodoRequest
            solicitudValida() {

        return new NodosPerifericosResource.AltaNodoRequest(
                TipoNodo.BALANZA,
                "Balanza Ruta 5",
                "Ruta 5 km 100",
                9001L
        );
    }

    @Test
    void listarDevuelve200ConNodos() {
        List<DatosNodo> nodos = List.of(nodoEjemplo());

        when(servicio.listarTodos()).thenReturn(nodos);

        Response respuesta = recurso.listar();

        assertEquals(200, respuesta.getStatus());
        assertSame(nodos, respuesta.getEntity());

        verify(servicio).listarTodos();
    }

    @Test
    void registrarNodoValidoDevuelve201() {
        DatosNodo nodo = nodoEjemplo();

        when(servicio.registrar(
                TipoNodo.BALANZA,
                "Balanza Ruta 5",
                "Ruta 5 km 100",
                9001L
        )).thenReturn(nodo);

        Response respuesta = recurso.registrar(
                solicitudValida());

        assertEquals(201, respuesta.getStatus());
        assertSame(nodo, respuesta.getEntity());

        verify(servicio).registrar(
                TipoNodo.BALANZA,
                "Balanza Ruta 5",
                "Ruta 5 km 100",
                9001L
        );
    }

    @Test
    void registrarSinCuerpoDevuelve400() {
        Response respuesta = recurso.registrar(null);

        assertEquals(400, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("datos_invalidos",
                error.get("error"));

        verifyNoInteractions(servicio);
    }

    @Test
    void registrarDatosInvalidosDevuelve400() {
        when(servicio.registrar(
                TipoNodo.BALANZA,
                "Balanza Ruta 5",
                "Ruta 5 km 100",
                9001L
        )).thenThrow(
                new DatosNodoInvalidosException(
                        "El nombre es inválido")
        );

        Response respuesta = recurso.registrar(
                solicitudValida());

        assertEquals(400, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("datos_invalidos",
                error.get("error"));

        assertEquals("El nombre es inválido",
                error.get("detalle"));
    }

    @Test
    void registrarNodoDuplicadoDevuelve409() {
        when(servicio.registrar(
                TipoNodo.BALANZA,
                "Balanza Ruta 5",
                "Ruta 5 km 100",
                9001L
        )).thenThrow(
                new NodoDuplicadoException(
                        TipoNodo.BALANZA, 9001L)
        );

        Response respuesta = recurso.registrar(
                solicitudValida());

        assertEquals(409, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("nodo_duplicado",
                error.get("error"));
    }

    @Test
    void listarSinAutenticacionDevuelve401() {
        when(seguridad.getUserPrincipal())
                .thenReturn(null);

        Response respuesta = recurso.listar();

        assertEquals(401, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("no_autenticado",
                error.get("error"));

        verifyNoInteractions(servicio);
    }

    @Test
    void registrarSinAutenticacionDevuelve401() {
        when(seguridad.getUserPrincipal())
                .thenReturn(null);

        Response respuesta = recurso.registrar(
                solicitudValida());

        assertEquals(401, respuesta.getStatus());

        verifyNoInteractions(servicio);
    }

    @Test
    void listarSinRolAdministradorDevuelve403() {
        when(seguridad.isUserInRole("ADMINISTRADOR"))
                .thenReturn(false);

        Response respuesta = recurso.listar();

        assertEquals(403, respuesta.getStatus());

        Map<?, ?> error =
                (Map<?, ?>) respuesta.getEntity();

        assertEquals("no_autorizado",
                error.get("error"));

        verifyNoInteractions(servicio);
    }

    @Test
    void registrarSinRolAdministradorDevuelve403() {
        when(seguridad.isUserInRole("ADMINISTRADOR"))
                .thenReturn(false);

        Response respuesta = recurso.registrar(
                solicitudValida());

        assertEquals(403, respuesta.getStatus());

        verifyNoInteractions(servicio);
    }
}
