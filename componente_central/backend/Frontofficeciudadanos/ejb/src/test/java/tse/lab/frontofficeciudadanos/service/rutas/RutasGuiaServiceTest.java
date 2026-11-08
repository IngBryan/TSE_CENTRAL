package tse.lab.frontofficeciudadanos.service.rutas;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
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
    private final GuiaDAO guias = mock(GuiaDAO.class);
    private final RutasAutorizadasService rutas = mock(RutasAutorizadasService.class);
    private final AsignacionRutasService asignacion = mock(AsignacionRutasService.class);
    private static final List<DatosRutaAutorizada> GUARDADAS = List.of(new DatosRutaAutorizada(7L, 1, "sha", 100, "geo"));
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
        when(guias.buscarPorId("G1")).thenReturn(guia);
        inyectar("autorizacion", autorizacion);
        inyectar("guias", guias);
        inyectar("rutas", rutas);
        inyectar("asignacion", asignacion);
        when(rutas.obtenerPorGuia("G1")).thenReturn(GUARDADAS);
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

    @Test
    void returnsTheRoutesOfItsOwnGuide() throws Exception {
        assertSame(GUARDADAS, servicio.consultar("ci", "G1", "t"));
    }

    @Test
    void aGuideOfAnotherCompanyDoesNotExist() {
        when(guias.buscarPorId("G2")).thenReturn(otraGuia(2L));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("ci", "G2", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("ci", "G3", "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("ci", null, "t"));
    }

    @Test
    void aGuideWithoutCompanyDoesNotExist() {
        when(guias.buscarPorId("G2")).thenReturn(otraGuia(null));

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("ci", "G2", "t"));
    }

    @Test
    void confirmingStoresTheRoutesOfThePreview() throws Exception {
        previewConfirmacion();
        when(geometrias.obtenerOCrear("geo", 100)).thenReturn(7L);

        assertSame(GUARDADAS, servicio.confirmar("ci", "G1", 1L, 2L, TOKEN, "t"));

        verify(asignacion).asociar("ci", "G1", 1L, 2L, List.of(7L), "t");
    }

    @Test
    void aPreviewIsRequired() {
        for (String id : new String[] { null, "corto" }) {
            RutasException e = assertThrows(RutasException.class,
                    () -> servicio.confirmar("ci", "G1", 1L, 2L, id, "t"));
            assertEquals(400, e.status());
            assertEquals("previsualizacion_requerida", e.codigo());
        }
    }

    @Test
    void anOutdatedPreviewIsAConflict() {
        previewConfirmacion();
        RutasException e = assertThrows(RutasException.class,
                () -> servicio.confirmar("ci", "G1", 1L, 2L, "b".repeat(64), "t"));

        assertEquals(409, e.status());
        assertEquals("rutas_actualizadas", e.codigo());
        verify(geometrias, never()).obtenerOCrear(anyString(), anyDouble());
    }

    @Test
    void ifAnotherTransactionInsertedTheGeometryItUsesThatOne() throws Exception {
        previewConfirmacion();
        when(geometrias.obtenerOCrear("geo", 100)).thenThrow(new IllegalStateException("unique"));
        when(geometrias.buscarId("sha")).thenReturn(9L);

        servicio.confirmar("ci", "G1", 1L, 2L, TOKEN, "t");

        verify(asignacion).asociar("ci", "G1", 1L, 2L, List.of(9L), "t");
    }

    @Test
    void aFailureThatLeftNoGeometryIsNotHidden() {
        previewConfirmacion();
        IllegalStateException fallo = new IllegalStateException("base caida");
        when(geometrias.obtenerOCrear("geo", 100)).thenThrow(fallo);
        when(geometrias.buscarId("sha")).thenReturn(null);

        assertSame(fallo, assertThrows(IllegalStateException.class,
                () -> servicio.confirmar("ci", "G1", 1L, 2L, TOKEN, "t")));
    }

    @Test
    void solicitudesSinDatosSeValidanSinConsultarElEnrutador() throws Exception {
        var altaInvalida = new DatosInvalidosException(Map.of("rubroCliente", "Obligatorio"));
        when(viajes.crear("ci", null, "t")).thenThrow(altaInvalida);
        assertSame(altaInvalida, assertThrows(DatosInvalidosException.class,
                () -> servicio.crearGuia("ci", null, "t")));

        var edicionInvalida = new ConflictoException("GUIA_DESACTUALIZADA", "Falta la version");
        when(viajes.editarConRuta("ci", "G1", null, null, "t")).thenThrow(edicionInvalida);
        assertSame(edicionInvalida, assertThrows(ConflictoException.class,
                () -> servicio.editarGuia("ci", "G1", null, "t")));
        verifyNoInteractions(planificador, geometrias);
    }

    @Test
    void editarConLosNombresOriginalesSinIdsNoExigeRecalcularLaRuta() throws Exception {
        var datos = new EdicionGuia("Agro", "Granos", 2000L, "2026-11-08",
                "Origen", "Destino", null, null, 0L, null);

        servicio.editarGuia("ci", "G1", datos, "t");

        verify(viajes).editarConRuta("ci", "G1", datos, null, "t");
        verifyNoInteractions(planificador, geometrias);
    }

    @Test
    void cambiarElOrigenEntregaLaNuevaRutaALaEdicion() throws Exception {
        preview(3L, 2L);
        var datos = new EdicionGuia("Agro", "Granos", 1000L, "2026-11-08",
                null, null, 3L, 2L, 0L, TOKEN);

        servicio.editarGuia("ci", "G1", datos, "t");

        verify(viajes).editarConRuta("ci", "G1", datos, List.of(9L), "t");
    }

    @Test
    void cambiarElNombreDelOrigenODestinoNoEvitaLaPrevisualizacion() {
        for (var datos : List.of(
                new EdicionGuia("Agro", "Granos", 1000L, "2026-11-08",
                        "Otro origen", "Destino", null, null, 0L, null),
                new EdicionGuia("Agro", "Granos", 1000L, "2026-11-08",
                        "Origen", "Otro destino", null, null, 0L, null))) {
            assertEquals("previsualizacion_requerida", assertThrows(RutasException.class,
                    () -> servicio.editarGuia("ci", "G1", datos, "t")).codigo());
        }
        verifyNoInteractions(viajes, planificador, geometrias);
    }

    @Test
    void unaLocalidadHistoricaSinCorrespondenciaNoReutilizaLaRuta() throws Exception {
        Field origen = Guia.class.getDeclaredField("origenLocalidad");
        origen.setAccessible(true);
        origen.set(guia, null);
        when(localidades.buscarUnicaPorNombre("Origen")).thenReturn(null);

        assertEquals("previsualizacion_requerida", assertThrows(RutasException.class,
                () -> servicio.editarGuia("ci", "G1", edicion(2L, null), "t")).codigo());

        verifyNoInteractions(viajes, planificador, geometrias);
    }

    private void previewConfirmacion() {
        var ruta = new PrevisualizacionRutasService.RutaVista(1, "sha", 100, 60, "geo");
        when(planificador.calcular(1L, 2L)).thenReturn(
                new PrevisualizacionRutasService.Previsualizacion(1L, 2L, TOKEN, List.of(ruta)));
    }

    private Guia otraGuia(Long numeroEmpresa) {
        Empresa otra = null;
        if (numeroEmpresa != null) {
            otra = new Empresa();
            otra.setNroEmpresa(numeroEmpresa);
        }
        return new Guia("G2", "Agro", 1000, LocalDate.now(), "Origen", "Destino", otra);
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
