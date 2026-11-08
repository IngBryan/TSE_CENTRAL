package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.EdicionGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.NuevaGuia;
import tse.lab.model.Empresa;
import tse.lab.model.Guia;
import tse.lab.model.Localidad;

class RutasGuiaServiceTest {
    private final RutasGuiaService servicio = new RutasGuiaService();
    private final GuiasService viajes = mock(GuiasService.class);
    private final PrevisualizacionRutasService planificador = mock(PrevisualizacionRutasService.class);
    private final GeometriasService geometrias = mock(GeometriasService.class);
    private final LocalidadDAO localidades = mock(LocalidadDAO.class);
    private Guia guia;
    private static final String TOKEN = "a".repeat(64);

    @BeforeEach
    void preparar() throws Exception {
        Empresa empresa = new Empresa(1L, "Empresa", "Empresa SA", "Montevideo", null);
        guia = new Guia("G1", "Agro", 1000, LocalDate.now(), "Origen", "Destino", empresa);
        guia.setOrigenLocalidad(new Localidad(1L, "Origen", "A", -34, -56));
        guia.setDestinoLocalidad(new Localidad(2L, "Destino", "B", -33, -55));
        AutorizacionResponsable autorizacion = mock(AutorizacionResponsable.class);
        when(autorizacion.empresaDelResponsable("ci", true, "t")).thenReturn(empresa);
        GuiaDAO guias = mock(GuiaDAO.class);
        when(guias.buscarPorId("G1")).thenReturn(guia);
        inyectar("autorizacion", autorizacion);
        inyectar("guias", guias);
        inyectar("localidades", localidades);
        inyectar("viajes", viajes);
        inyectar("planificador", planificador);
        inyectar("geometrias", geometrias);
    }

    @Test
    void altaSinPrevisualizacionNoCreaUnaGuiaIncompleta() {
        NuevaGuia datos = new NuevaGuia("Agro", "Granos", 1000L, "2026-11-08", null, null, 1L, 2L, null);
        assertEquals("previsualizacion_requerida", assertThrows(RutasException.class,
                () -> servicio.crearGuia("ci", datos, "t")).codigo());
        verifyNoInteractions(viajes, planificador, geometrias);
    }

    @Test
    void altaConfirmaLaUnicaRutaCalculada() throws Exception {
        NuevaGuia datos = new NuevaGuia("Agro", "Granos", 1000L, "2026-11-08", null, null, 1L, 2L, TOKEN);
        preview(1L, 2L);
        servicio.crearGuia("ci", datos, "t");
        verify(viajes).crearConRuta("ci", datos, List.of(9L), "t");
    }

    @Test
    void editarSinRutaYConMismasLocalidadesNoConsultaOsrm() throws Exception {
        EdicionGuia datos = edicion(2L, null);
        servicio.editarGuia("ci", "G1", datos, "t");
        verify(viajes).editarConRuta("ci", "G1", datos, null, "t");
        verifyNoInteractions(planificador, geometrias);
    }

    @Test
    void editarGuiaHistoricaSinIdsNoExigeRutaSiSeMantieneElRecorrido() throws Exception {
        // Simula una guia anterior al catalogo que conserva nombres univocos.
        Field origen = Guia.class.getDeclaredField("origenLocalidad");
        origen.setAccessible(true);
        Localidad anterior = guia.getOrigenLocalidad();
        origen.set(guia, null);
        when(localidades.buscarUnicaPorNombre("Origen")).thenReturn(anterior);
        EdicionGuia datos = edicion(2L, null);
        servicio.editarGuia("ci", "G1", datos, "t");
        verify(viajes).editarConRuta("ci", "G1", datos, null, "t");
        verifyNoInteractions(planificador, geometrias);
    }

    @Test
    void cambiarLocalidadesExigePrevisualizarYNoModificaAntesDeConfirmar() {
        assertThrows(RutasException.class, () -> servicio.editarGuia("ci", "G1", edicion(3L, null), "t"));
        verifyNoInteractions(viajes, planificador, geometrias);
    }

    @Test
    void cambioDeLocalidadesEntregaNuevaRutaALaTransaccionDeEdicion() throws Exception {
        preview(1L, 3L);
        EdicionGuia datos = edicion(3L, TOKEN);
        servicio.editarGuia("ci", "G1", datos, "t");
        verify(viajes).editarConRuta("ci", "G1", datos, List.of(9L), "t");
    }

    @Test
    void rutaDesactualizadaNoModificaLaGuia() {
        preview(1L, 3L);
        assertEquals("rutas_actualizadas", assertThrows(RutasException.class,
                () -> servicio.editarGuia("ci", "G1", edicion(3L, "b".repeat(64)), "t")).codigo());
        verifyNoInteractions(viajes, geometrias);
    }

    private EdicionGuia edicion(Long destino, String token) {
        return new EdicionGuia("Otro rubro", "Granos", 1000L, "2026-11-08", null, null, 1L, destino, 0L, token);
    }

    private void preview(Long origen, Long destino) {
        var ruta = new PrevisualizacionRutasService.RutaVista(1, "hash", 1000, 100, "geojson");
        when(planificador.calcular(origen, destino)).thenReturn(
                new PrevisualizacionRutasService.Previsualizacion(origen, destino, TOKEN, List.of(ruta)));
        when(geometrias.obtenerOCrear("geojson", 1000)).thenReturn(9L);
    }

    private void inyectar(String nombre, Object valor) throws Exception {
        Field campo = RutasGuiaService.class.getDeclaredField(nombre);
        campo.setAccessible(true);
        campo.set(servicio, valor);
    }
}
