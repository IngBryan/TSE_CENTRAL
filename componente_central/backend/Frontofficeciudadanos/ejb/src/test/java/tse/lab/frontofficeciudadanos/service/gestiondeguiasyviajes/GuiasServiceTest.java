package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tse.lab.frontofficeciudadanos.data.ChoferDAO;
import tse.lab.frontofficeciudadanos.data.EventoViajeDAO;
import tse.lab.frontofficeciudadanos.data.GuiaDAO;
import tse.lab.frontofficeciudadanos.data.LocalidadDAO;
import tse.lab.frontofficeciudadanos.data.ModificacionGuiaDAO;
import tse.lab.frontofficeciudadanos.data.RubroDAO;
import tse.lab.frontofficeciudadanos.data.TipoCargaDAO;
import tse.lab.frontofficeciudadanos.data.CiudadanoDAO;
import tse.lab.frontofficeciudadanos.data.ResponsableDAO;
import tse.lab.frontofficeciudadanos.data.RutaReferenciaDAO;
import tse.lab.frontofficeciudadanos.data.VehiculoDAO;
import tse.lab.frontofficeciudadanos.service.apimovil.ViajesChoferService;
import tse.lab.frontofficeciudadanos.service.AutorizacionResponsable;
import tse.lab.frontofficeciudadanos.service.ConflictoException;
import tse.lab.frontofficeciudadanos.service.DatosInvalidosException;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.GuiaVista;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.NuevaGuia;
import tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes.GuiasService.OpcionesAsignacion;
import tse.lab.frontofficeciudadanos.service.NoAutorizadoException;
import tse.lab.frontofficeciudadanos.service.RecursoNoEncontradoException;
import tse.lab.frontofficeciudadanos.service.rutas.AsignacionRutasService;
import tse.lab.frontofficeciudadanos.service.rutas.RutasException;
import tse.lab.model.Chofer;
import tse.lab.model.Empresa;
import tse.lab.model.EstadoChofer;
import tse.lab.model.EstadoGuia;
import tse.lab.model.EstadoValidacion;
import tse.lab.model.Guia;
import tse.lab.model.Localidad;
import tse.lab.model.Ciudadano;
import tse.lab.model.Responsable;
import tse.lab.model.Rubro;
import tse.lab.model.RutaReferencia;
import tse.lab.model.TipoCarga;
import tse.lab.model.Vehiculo;

// Gestion de Viajes de Carga y CU-07 contra H2 con los DAOs reales.
class GuiasServiceTest {

    private static final String RESPONSABLE = "54074380";
    private static final String MANANA = LocalDate.now(ZoneId.of("America/Montevideo")).plusDays(1).toString();

    private EntityManagerFactory emf;
    private EntityManager em;
    private GuiasService servicio;
    private Long choferHabilitado;
    private Long choferPendiente;
    private Long choferOtraEmpresa;

    @BeforeEach
    void setUp() throws Exception {
        emf = Persistence.createEntityManagerFactory("frontofficeciudadanosTestPersistenceUnit");
        em = emf.createEntityManager();
        CiudadanoDAO perfiles = conEm(new CiudadanoDAO());
        AutorizacionResponsable autorizacion = new AutorizacionResponsable();
        set(autorizacion, "responsables", conEm(new ResponsableDAO()));
        set(autorizacion, "perfiles", perfiles);
        servicio = new GuiasService();
        set(servicio, "autorizacion", autorizacion);
        set(servicio, "guias", conEm(new GuiaDAO()));
        set(servicio, "localidades", conEm(new LocalidadDAO()));
        set(servicio, "vehiculos", conEm(new VehiculoDAO()));
        set(servicio, "choferes", conEm(new ChoferDAO()));
        set(servicio, "perfiles", perfiles);
        set(servicio, "eventos", conEm(new EventoViajeDAO()));
        set(servicio, "modificaciones", conEm(new ModificacionGuiaDAO()));
        set(servicio, "tiposCarga", conEm(new TipoCargaDAO()));
        set(servicio, "rubros", conEm(new RubroDAO()));
        AsignacionRutasService asignacionRutas = new AsignacionRutasService();
        set(asignacionRutas, "autorizacion", autorizacion);
        set(asignacionRutas, "guias", conEm(new GuiaDAO()));
        set(asignacionRutas, "localidades", conEm(new LocalidadDAO()));
        set(asignacionRutas, "rutas", conEm(new RutaReferenciaDAO()));
        set(servicio, "asignacionRutas", asignacionRutas);

        Empresa empresa = new Empresa(1L, "Transportes del Este", "TDE S.A.", "Pando", null);
        empresa.setHabilitada(true);
        Empresa otra = new Empresa(2L, "Otra", "Otra S.A.", "Montevideo", null);
        otra.setHabilitada(true);
        Ciudadano responsable = validado(RESPONSABLE, "Emiliano", "Tomas");
        Ciudadano bryan = validado("56087406", "Bryan", "Salamone");
        Ciudadano personaPendiente = new Ciudadano("22222222", null, null);
        Ciudadano personaDeOtra = new Ciudadano("77777777", null, null);
        Chofer habilitado = new Chofer(bryan, empresa, EstadoChofer.HABILITADO);
        Chofer pendiente = new Chofer(personaPendiente, empresa, EstadoChofer.PENDIENTE_VALIDACION);
        Chofer deOtra = new Chofer(personaDeOtra, otra, EstadoChofer.HABILITADO);
        em.getTransaction().begin();
        em.persist(empresa);
        em.persist(otra);
        em.persist(new Rubro(1L, "Agropecuario"));
        em.persist(new Rubro(2L, "Forestal"));
        em.persist(new TipoCarga(1L, "Granos a granel"));
        em.persist(new TipoCarga(2L, "Rollizos"));
        em.persist(new Localidad(1L, "Montevideo", "Montevideo", -34.90, -56.17));
        em.persist(new Localidad(2L, "Paysandú", "Paysandú", -32.32, -58.08));
        em.persist(new Localidad(3L, "Salto", "Salto", -31.38, -57.96));
        em.persist(new Localidad(4L, "Rivera", "Rivera", -30.91, -55.55));
        // Mismo nombre en departamentos distintos: los IDs eliminan ambiguedad.
        em.persist(new Localidad(5L, "San José", "San José", -34.34, -56.71));
        em.persist(new Localidad(6L, "San José", "Colonia", -34.30, -57.20));
        em.persist(responsable);
        em.persist(new Responsable(responsable, empresa));
        em.persist(bryan);
        em.persist(personaPendiente);
        em.persist(personaDeOtra);
        em.persist(new Vehiculo("SBA 1234", "Scania", "R 450", 9000, 26000, empresa));
        em.persist(new Vehiculo("OTR 0001", "Ford", "Cargo", 5000, 8000, otra));
        em.persist(habilitado);
        em.persist(pendiente);
        em.persist(deOtra);
        em.getTransaction().commit();
        choferHabilitado = habilitado.getId();
        choferPendiente = pendiente.getId();
        choferOtraEmpresa = deOtra.getId();
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    @Test
    void dtoRestNoTienenConstructoresAlternativos() {
        // JSON-B debe poder elegir sin ambiguedad el constructor canonico de cada record.
        assertEquals(1, NuevaGuia.class.getDeclaredConstructors().length);
        assertEquals(1, GuiasService.EdicionGuia.class.getDeclaredConstructors().length);
    }

    @Test
    void crearGuiaQuedaSinAsignarYAparaceEnElListado() throws Exception {
        GuiaVista g = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia(" Agropecuario ", " Granos  a granel ", 18000L, MANANA, "Montevideo", "Paysandú", null, null, null), "t"));
        assertTrue(g.id().startsWith("GV-"));
        assertEquals("Agropecuario", g.rubroCliente());
        assertEquals("Granos a granel", g.tipoCarga());
        assertEquals("SIN_ASIGNAR", g.estado());
        assertNull(g.vehiculo());
        assertEquals(List.of(g), servicio.listar(RESPONSABLE, "t"));
    }

    @Test
    void elTipoDeCargaTieneQueSerDelCatalogoYSeGuardaConSuNombre() throws Exception {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("Agropecuario", "Cohetes", 18000L, MANANA, "Montevideo", "Paysandú", null, null, null), "t"));
        assertEquals(Map.of("tipoCarga", "Elegí un tipo de carga de la lista."), e.getErrores());

        GuiaVista g = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia("Forestal", "ROLLIZOS", 18000L, MANANA, "Rivera", "Montevideo", null, null, null), "t"));
        assertEquals("Rollizos", g.tipoCarga());
    }

    @Test
    void elRubroDelClienteTieneQueSerDelCatalogoYSeGuardaConSuNombre() throws Exception {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("Agro", "Rollizos", 18000L, MANANA, "Rivera", "Montevideo", null, null, null), "t"));
        assertEquals(Map.of("rubroCliente", "Elegí un rubro de la lista."), e.getErrores());

        GuiaVista g = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia("forestal", "Rollizos", 18000L, MANANA, "Rivera", "Montevideo", null, null, null), "t"));
        assertEquals("Forestal", g.rubroCliente());
        assertEquals(List.of(new GuiasService.RubroVista(1L, "Agropecuario"), new GuiasService.RubroVista(2L, "Forestal")),
                servicio.rubros(RESPONSABLE, "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.rubros("99999999", "t"));
    }

    @Test
    void elCatalogoDeTiposDeCargaVieneOrdenadoYSoloParaElResponsable() throws Exception {
        assertEquals(List.of(new GuiasService.TipoCargaVista(1L, "Granos a granel"),
                new GuiasService.TipoCargaVista(2L, "Rollizos")), servicio.tiposCarga(RESPONSABLE, "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.tiposCarga("99999999", "t"));
    }

    @Test
    void crearConIdsPersisteLaReferenciaYElNombreCanonico() throws Exception {
        GuiaVista creada = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 10000L, MANANA, null, null, 4L, 1L, null), "t"));
        assertEquals(4L, creada.origenId());
        assertEquals(1L, creada.destinoId());
        assertEquals("Rivera", creada.origen());
        em.clear();
        Guia guardada = em.find(Guia.class, creada.id());
        assertEquals(4L, guardada.getOrigenLocalidad().getId());
        assertEquals(1L, guardada.getDestinoLocalidad().getId());
    }

    @Test
    void losIdsRechazanLocalidadesInexistentesIgualesOMensajesContradictorios() {
        DatosInvalidosException desconocida = assertThrows(DatosInvalidosException.class,
                () -> servicio.crear(RESPONSABLE,
                        new NuevaGuia("Forestal", "Rollizos", 1000L, MANANA, null, null, 9999L, 1L, null), "t"));
        assertTrue(desconocida.getErrores().containsKey("origenId"));
        DatosInvalidosException iguales = assertThrows(DatosInvalidosException.class,
                () -> servicio.crear(RESPONSABLE,
                        new NuevaGuia("Forestal", "Rollizos", 1000L, MANANA, null, null, 1L, 1L, null), "t"));
        assertTrue(iguales.getErrores().containsKey("destinoId"));
        DatosInvalidosException contradiccion = assertThrows(DatosInvalidosException.class,
                () -> servicio.crear(RESPONSABLE,
                        new NuevaGuia("Forestal", "Rollizos", 1000L, MANANA, "Salto", null, 4L, 1L, null), "t"));
        assertTrue(contradiccion.getErrores().containsKey("origen"));
    }

    @Test
    void nombreAmbiguoNoSeAceptaPeroIdsDistinguenDepartamentos() throws Exception {
        DatosInvalidosException ambiguo = assertThrows(DatosInvalidosException.class,
                () -> servicio.crear(RESPONSABLE,
                        new NuevaGuia("Forestal", "Rollizos", 1000L, MANANA, "San José", "Montevideo", null, null, null), "t"));
        assertTrue(ambiguo.getErrores().containsKey("origen"));
        GuiaVista creada = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 1000L, MANANA, null, null, 6L, 1L, null), "t"));
        assertEquals(6L, creada.origenId());
        assertEquals("San José", creada.origen());
    }

    @Test
    void validacionesAlCrear() {
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("", null, 0L, "2020-01-01", "Salto", " salto ", null, null, null), "t"));
        assertEquals(5, e.getErrores().size(), e.getErrores().toString());
        assertTrue(e.getErrores().get("tipoCarga").contains("obligatorio"));
        assertTrue(e.getErrores().containsKey("rubroCliente"));
        assertTrue(e.getErrores().get("pesoCargaKg").contains("mayor que 0"));
        assertTrue(e.getErrores().get("fecha").contains("anterior a hoy"));
        assertTrue(e.getErrores().get("destino").contains("distinto"));
        assertTrue(assertThrows(DatosInvalidosException.class, () -> servicio.crear(RESPONSABLE,
                new NuevaGuia("x", "y", 1L, "10/10/2026", "a", "b", null, null, null), "t")).getErrores().containsKey("fecha"));
    }

    @Test
    void opcionesListanVehiculosYSoloChoferesHabilitadosDeLaEmpresa() throws Exception {
        GuiaVista g = nuevaGuia();
        OpcionesAsignacion o = servicio.opcionesAsignacion(RESPONSABLE, g.id(), "t");
        assertEquals(List.of("SBA 1234"), o.vehiculos().stream().map(GuiasService.VehiculoResumen::matricula).toList());
        assertEquals(1, o.choferes().size());
        assertEquals(choferHabilitado, o.choferes().get(0).id());
        assertEquals("Bryan", o.choferes().get(0).primerNombre());
    }

    @Test
    void asignarRegistraYLaGuiaNoSePuedeVolverAAsignar() throws Exception {
        GuiaVista g = nuevaGuia();
        GuiaVista asignada = enTx(() -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
        assertEquals("ASIGNADA", asignada.estado());
        assertEquals("SBA 1234", asignada.vehiculo().matricula());
        assertEquals("56087406", asignada.chofer().cedula());
        em.clear();
        assertEquals(EstadoGuia.ASIGNADA, em.find(Guia.class, g.id()).getEstado());

        ConflictoException e = assertThrows(ConflictoException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
        assertEquals("GUIA_YA_ASIGNADA", e.getCodigo());
        assertThrows(ConflictoException.class, () -> servicio.opcionesAsignacion(RESPONSABLE, g.id(), "t"));
    }

    @Test
    void noSeAsignaVehiculoOChoferDeOtraEmpresaNiChoferPendiente() throws Exception {
        GuiaVista g = nuevaGuia();
        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "OTR 0001", choferOtraEmpresa, "t"));
        assertEquals(2, e.getErrores().size());
        e = assertThrows(DatosInvalidosException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferPendiente, "t"));
        assertTrue(e.getErrores().get("chofer").contains("pendiente"));
        assertEquals("SIN_ASIGNAR", servicio.listar(RESPONSABLE, "t").get(0).estado());
    }

    @Test
    void altaGuardaRutaAntesDeAsignarVehiculoYChofer() throws Exception {
        Long ruta = rutaCalculada();
        GuiaVista creada = enTx(() -> servicio.crearConRuta(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 20000L, MANANA, null, null, 4L, 1L, null), List.of(ruta), "t"));
        em.clear();
        Guia guardada = em.find(Guia.class, creada.id());
        assertEquals(EstadoGuia.SIN_ASIGNAR, guardada.getEstado());
        assertNull(guardada.getVehiculo());
        assertNull(guardada.getChofer());
        assertEquals(ruta, guardada.getRutasAutorizadas().getFirst().getRutaReferencia().getId());
        GuiaVista asignada = enTx(() -> servicio.asignar(RESPONSABLE, creada.id(), "SBA 1234", choferHabilitado, "t"));
        em.clear();
        assertEquals("ASIGNADA", asignada.estado());
        assertEquals(ruta, em.find(Guia.class, creada.id()).getRutasAutorizadas().getFirst().getRutaReferencia().getId());
    }

    @Test
    void falloDeRutaRevierteElAltaCompleta() throws Exception {
        falla(RutasException.class, () -> servicio.crearConRuta(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 20000L, MANANA, null, null, 4L, 1L, null), List.of(-1L), "t"));
        em.clear();
        assertTrue(servicio.listar(RESPONSABLE, "t").isEmpty());
    }

    @Test
    void guiaSinAsignarYSinRutaPermiteEditarSinAsignarla() throws Exception {
        GuiaVista g = nuevaGuia();
        GuiaVista editada = enTx(() -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Agropecuario", "Rollizos", 21000L, MANANA, null, null, 4L, 1L, g.version(), null), "t"));
        em.clear();
        Guia guardada = em.find(Guia.class, g.id());
        assertEquals("SIN_ASIGNAR", editada.estado());
        assertEquals("Agropecuario", guardada.getRubroCliente());
        assertTrue(guardada.getRutasAutorizadas().isEmpty());
        assertTrue(editada.version() > g.version());
        assertEquals("EDICION", servicio.eventos(RESPONSABLE, g.id(), "t").modificaciones().getFirst().tipo());
    }

    @Test
    void editarLocalidadesReemplazaRutaEnLaMismaTransaccion() throws Exception {
        Long rutaAnterior = rutaCalculada();
        GuiaVista g = enTx(() -> servicio.crearConRuta(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 20000L, MANANA, null, null, 4L, 1L, null), List.of(rutaAnterior), "t"));
        Long rutaNueva = enTx(() -> {
            RutaReferencia ruta = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[-55.55,-30.91],[-57.96,-31.38]]}", 300000);
            em.persist(ruta);
            return ruta.getId();
        });
        GuiaVista editada = enTx(() -> servicio.editarConRuta(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Forestal", "Rollizos", 20000L, MANANA, null, null, 4L, 3L, g.version(), null),
                List.of(rutaNueva), "t"));
        em.clear();
        Guia guardada = em.find(Guia.class, g.id());
        assertEquals(3L, editada.destinoId());
        assertEquals(rutaNueva, guardada.getRutasAutorizadas().getFirst().getRutaReferencia().getId());
        assertTrue(em.find(RutaReferencia.class, rutaAnterior) != null);
    }

    @Test
    void falloAlReemplazarRutaRevierteLocalidadesYCarga() throws Exception {
        Long ruta = rutaCalculada();
        GuiaVista g = enTx(() -> servicio.crearConRuta(RESPONSABLE,
                new NuevaGuia("Forestal", "Rollizos", 20000L, MANANA, null, null, 4L, 1L, null), List.of(ruta), "t"));
        falla(RutasException.class, () -> servicio.editarConRuta(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Agropecuario", "Rollizos", 21000L, MANANA, null, null, 4L, 3L, g.version(), null),
                List.of(-1L), "t"));
        em.clear();
        Guia guardada = em.find(Guia.class, g.id());
        assertEquals(1L, guardada.getDestinoLocalidad().getId());
        assertEquals(20000L, guardada.getPesoCargaKg());
        assertEquals(ruta, guardada.getRutasAutorizadas().getFirst().getRutaReferencia().getId());
    }

    @Test
    void unVehiculoDadoDeBajaNoSeOfreceNiSeAsigna() throws Exception {
        GuiaVista g = nuevaGuia();
        enTx(() -> {
            em.find(Vehiculo.class, "SBA 1234").setActivo(false);
            return null;
        });
        assertTrue(servicio.opcionesAsignacion(RESPONSABLE, g.id(), "t").vehiculos().isEmpty());
        assertTrue(assertThrows(DatosInvalidosException.class,
                () -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t")).getErrores()
                .containsKey("vehiculo"));
    }

    @Test
    void guiaInexistenteOSinPermiso() throws Exception {
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.opcionesAsignacion(RESPONSABLE, "GV-NOEXISTE", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.listar("56087406", "t"));
        assertThrows(NoAutorizadoException.class,
                () -> servicio.crear(null, new NuevaGuia("x", "y", 1L, MANANA, "a", "b", null, null, null), "t"));
        // la empresa tiene que estar habilitada
        enTx(() -> {
            em.find(Empresa.class, 1L).setHabilitada(false);
            return null;
        });
        assertThrows(NoAutorizadoException.class, () -> servicio.listar(RESPONSABLE, "t"));
    }

    // Historial de eventos de la app movil en la web del responsable (los CONFLICTO se marcan)
    @Test
    void historialDeEventosDeLaGuiaConConflictosYFoto() throws Exception {
        GuiaVista g = nuevaGuia();
        enTx(() -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
        ViajesChoferService movil = new ViajesChoferService();
        set(movil, "choferes", conEm(new ChoferDAO()));
        set(movil, "guias", conEm(new GuiaDAO()));
        set(movil, "eventos", conEm(new EventoViajeDAO()));
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10 };
        String fin = "00000000-0000-0000-0000-000000000001";
        String inicio = "00000000-0000-0000-0000-000000000002";
        String incidente = "00000000-0000-0000-0000-000000000003";
        // llegan desordenados: el FIN (sin iniciar) primero
        enTx(() -> movil.registrarEvento("56087406", new ViajesChoferService.NuevoEvento(fin, g.id(), "FIN",
                "2026-10-11T10:00:00-03:00", null, null, null, null, null), "t"));
        enTx(() -> movil.registrarEvento("56087406", new ViajesChoferService.NuevoEvento(inicio, g.id(), "INICIO",
                "2026-10-11T08:00:00-03:00", null, null, null, null, null), "t"));
        enTx(() -> movil.registrarEvento("56087406", new ViajesChoferService.NuevoEvento(incidente, g.id(),
                "INCIDENTE", "2026-10-11T09:00:00-03:00", "Pinchadura", -34.9, -56.1,
                java.util.Base64.getEncoder().encodeToString(jpeg), null), "t"));

        GuiasService.GuiaConEventos detalle = servicio.eventos(RESPONSABLE, g.id(), "t");
        assertEquals("EN_CURSO", detalle.guia().estado());
        // ordenados por la hora del dispositivo
        assertEquals(List.of(inicio, incidente, fin),
                detalle.eventos().stream().map(GuiasService.EventoVista::idEvento).toList());
        GuiasService.EventoVista conflicto = detalle.eventos().get(2);
        assertEquals("CONFLICTO", conflicto.resultado());
        assertEquals("VIAJE_NO_INICIADO", conflicto.codigo());
        assertEquals("Bryan", detalle.eventos().get(0).chofer().primerNombre());
        assertTrue(detalle.eventos().get(1).conFoto());
        assertEquals(6, servicio.fotoEvento(RESPONSABLE, g.id(), incidente, "t").length);
        assertNull(servicio.fotoEvento(RESPONSABLE, g.id(), inicio, "t"));
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.eventos(RESPONSABLE, "GV-NOEXISTE", "t"));
        assertThrows(NoAutorizadoException.class, () -> servicio.eventos("56087406", g.id(), "t"));
    }

    // ===== Modificacion de guias (solo ASIGNADA) =====

    @Test
    void editarGuiaAsignadaRegistraLosCambiosYSubeLaVersion() throws Exception {
        GuiaVista g = asignada();
        GuiaVista editada = enTx(() -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Forestal", "Rollizos", 21000L, MANANA, "Rivera", "Salto", null, null, g.version(), null), "t"));
        assertEquals("Salto", editada.destino());
        assertEquals(21000, editada.pesoCargaKg());
        assertEquals("ASIGNADA", editada.estado());
        assertEquals(g.version() + 1, editada.version());

        GuiasService.ModificacionVista m = servicio.eventos(RESPONSABLE, g.id(), "t").modificaciones().get(0);
        assertEquals("EDICION", m.tipo());
        assertEquals(RESPONSABLE, m.ciResponsable());
        assertTrue(m.cambios().contains("destino: Montevideo (#1) -> Salto (#3)"), m.cambios());
        assertTrue(m.cambios().contains("carga (kg): 20000 -> 21000"), m.cambios());
        assertEquals(g.version(), m.versionAnterior());
        assertEquals(editada.version(), m.versionNueva());
    }

    @Test
    void editarValidaLosDatosYQueHayaCambios() throws Exception {
        GuiaVista g = asignada();
        DatosInvalidosException e = falla(DatosInvalidosException.class, () -> servicio.editar(RESPONSABLE,
                g.id(), new GuiasService.EdicionGuia("", "x", 0L, "2020-01-01", "A", "a", null, null, g.version(), null), "t"));
        assertTrue(e.getErrores().keySet().containsAll(List.of("rubroCliente", "pesoCargaKg", "fecha", "destino")));
        e = falla(DatosInvalidosException.class, () -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Forestal", "Rollizos", 20000L, MANANA, "Rivera", "Montevideo", null, null, g.version(), null), "t"));
        assertTrue(e.getErrores().containsKey("general"));
    }

    @Test
    void soloSeModificaUnaGuiaAsignadaYConLaVersionActual() throws Exception {
        GuiaVista sinAsignar = nuevaGuia();
        assertEquals("GUIA_NO_MODIFICABLE", falla(ConflictoException.class, () -> servicio.cancelar(RESPONSABLE,
                sinAsignar.id(), new GuiasService.Cancelacion("x", sinAsignar.version()), "t")).getCodigo());
        GuiaVista g = asignada();
        // otra sesion la modifico mientras se editaba: version vieja
        enTx(() -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Forestal", "Rollizos", 1000L, MANANA, "Rivera", "Montevideo", null, null, g.version(), null), "t"));
        assertEquals("GUIA_DESACTUALIZADA", falla(ConflictoException.class, () -> servicio.editar(RESPONSABLE,
                g.id(), new GuiasService.EdicionGuia("Forestal", "Rollizos", 2000L, MANANA, "Rivera", "Montevideo", null, null,
                        g.version(), null), "t")).getCodigo());
        // el chofer la inicio: ya no se modifica
        enTx(() -> {
            em.find(Guia.class, g.id()).cambiarEstado(EstadoGuia.EN_CURSO);
            return null;
        });
        Long actual = em.find(Guia.class, g.id()).getVersion();
        assertEquals("GUIA_NO_MODIFICABLE", falla(ConflictoException.class, () -> servicio.reasignar(RESPONSABLE,
                g.id(), new GuiasService.Reasignacion("SBA 1234", choferHabilitado, actual), "t")).getCodigo());
        falla(RecursoNoEncontradoException.class, () -> servicio.cancelar(RESPONSABLE, "GV-NOEXISTE",
                new GuiasService.Cancelacion("x", 0L), "t"));
        falla(NoAutorizadoException.class, () -> servicio.cancelar("56087406", g.id(),
                new GuiasService.Cancelacion("x", actual), "t"));
    }

    @Test
    void reasignarCambiaVehiculoYChoferYLoRegistra() throws Exception {
        Long otroChofer = otroChoferHabilitado();
        GuiaVista g = asignada();
        assertTrue(falla(DatosInvalidosException.class, () -> servicio.reasignar(RESPONSABLE, g.id(),
                new GuiasService.Reasignacion("SBA 1234", choferPendiente, g.version()), "t")).getErrores().get("chofer")
                .contains("pendiente"));
        assertTrue(falla(DatosInvalidosException.class, () -> servicio.reasignar(RESPONSABLE, g.id(),
                new GuiasService.Reasignacion("OTR 0001", otroChofer, g.version()), "t")).getErrores().containsKey("vehiculo"));
        assertTrue(falla(DatosInvalidosException.class, () -> servicio.reasignar(RESPONSABLE, g.id(),
                new GuiasService.Reasignacion("SBA 1234", choferHabilitado, g.version()), "t")).getErrores()
                .containsKey("general"));

        GuiaVista r = enTx(() -> servicio.reasignar(RESPONSABLE, g.id(),
                new GuiasService.Reasignacion("SBA 1234", otroChofer, g.version()), "t"));
        assertEquals("33333333", r.chofer().cedula());
        assertEquals("ASIGNADA", r.estado());
        GuiasService.ModificacionVista m = servicio.eventos(RESPONSABLE, g.id(), "t").modificaciones().get(0);
        assertEquals("REASIGNACION", m.tipo());
        assertEquals("chofer: 56087406 -> 33333333", m.cambios());
    }

    @Test
    void cancelarRequiereMotivoYLaGuiaQuedaCancelada() throws Exception {
        GuiaVista g = asignada();
        assertTrue(falla(DatosInvalidosException.class, () -> servicio.cancelar(RESPONSABLE, g.id(),
                new GuiasService.Cancelacion("  ", g.version()), "t")).getErrores().containsKey("motivo"));
        GuiaVista c = enTx(() -> servicio.cancelar(RESPONSABLE, g.id(),
                new GuiasService.Cancelacion("El cliente suspendió el envío", g.version()), "t"));
        assertEquals("CANCELADA", c.estado());
        GuiasService.GuiaConEventos detalle = servicio.eventos(RESPONSABLE, g.id(), "t");
        assertEquals("CANCELACION", detalle.modificaciones().get(0).tipo());
        assertEquals("El cliente suspendió el envío", detalle.modificaciones().get(0).motivo());
        assertTrue(detalle.vehiculos().isEmpty()); // ya no se ofrecen opciones para reasignar
    }

    // Integrado con la app movil: el chofer estaba sin conexion con la version anterior de la guia
    @Test
    void modificacionesDelResponsableYEventosDelChoferSinConexion() throws Exception {
        ViajesChoferService movil = movil();
        Long otroChofer = otroChoferHabilitado();

        // 1. Editar -> el evento con la version vieja se aplica y avisa guiaModificada con los datos nuevos
        GuiaVista g1 = asignada();
        enTx(() -> servicio.editar(RESPONSABLE, g1.id(),
                new GuiasService.EdicionGuia("Forestal", "Rollizos", 20000L, MANANA, "Rivera", "Salto", null, null, g1.version(), null), "t"));
        ViajesChoferService.ResultadoEvento r1 = enTx(() -> movil.registrarEvento("56087406",
                eventoMovil(g1.id(), "INICIO", g1.version()), "t"));
        assertEquals("APLICADO", r1.resultado());
        assertTrue(r1.guiaModificada());
        assertEquals("Salto", r1.guia().destino());

        // 2. Reasignar a otro chofer -> el evento del chofer anterior queda CONFLICTO / GUIA_REASIGNADA
        GuiaVista g2 = asignada();
        enTx(() -> servicio.reasignar(RESPONSABLE, g2.id(),
                new GuiasService.Reasignacion("SBA 1234", otroChofer, g2.version()), "t"));
        ViajesChoferService.ResultadoEvento r2 = enTx(() -> movil.registrarEvento("56087406",
                eventoMovil(g2.id(), "INICIO", g2.version()), "t"));
        assertEquals("CONFLICTO", r2.resultado());
        assertEquals("GUIA_REASIGNADA", r2.codigo());
        assertNull(r2.guia());
        assertEquals("ASIGNADA", servicio.eventos(RESPONSABLE, g2.id(), "t").guia().estado());

        // 3. Cancelar -> el evento queda CONFLICTO / GUIA_CANCELADA y la guia sale de la app
        GuiaVista g3 = asignada();
        enTx(() -> servicio.cancelar(RESPONSABLE, g3.id(), new GuiasService.Cancelacion("Sin carga", g3.version()), "t"));
        ViajesChoferService.ResultadoEvento r3 = enTx(() -> movil.registrarEvento("56087406",
                eventoMovil(g3.id(), "INICIO", g3.version()), "t"));
        assertEquals("CONFLICTO", r3.resultado());
        assertEquals("GUIA_CANCELADA", r3.codigo());
        assertTrue(movil.viajes("56087406", "t").stream().noneMatch(v -> v.id().equals(g3.id())));

        // la linea de tiempo de la guia tiene la cancelacion y el evento en conflicto
        GuiasService.GuiaConEventos detalle = servicio.eventos(RESPONSABLE, g3.id(), "t");
        assertEquals(1, detalle.modificaciones().size());
        assertEquals("CONFLICTO", detalle.eventos().get(0).resultado());
    }

    // Las modificaciones bloquean la guia (PESSIMISTIC_WRITE): en el contenedor siempre hay transaccion
    private <E extends Exception> E falla(Class<E> tipo, Accion<?> accion) {
        return assertThrows(tipo, () -> enTx(accion));
    }

    private GuiaVista asignada() throws Exception {
        GuiaVista g = nuevaGuia();
        return enTx(() -> servicio.asignar(RESPONSABLE, g.id(), "SBA 1234", choferHabilitado, "t"));
    }

    private Long otroChoferHabilitado() throws Exception {
        return enTx(() -> {
            Ciudadano persona = new Ciudadano("33333333", null, null);
            em.persist(persona);
            Chofer c = new Chofer(persona, em.find(Empresa.class, 1L), EstadoChofer.HABILITADO);
            em.persist(c);
            return c;
        }).getId();
    }

    private ViajesChoferService movil() throws Exception {
        ViajesChoferService movil = new ViajesChoferService();
        set(movil, "choferes", conEm(new ChoferDAO()));
        set(movil, "guias", conEm(new GuiaDAO()));
        set(movil, "eventos", conEm(new EventoViajeDAO()));
        return movil;
    }

    private static ViajesChoferService.NuevoEvento eventoMovil(String guia, String tipo, Long version) {
        return new ViajesChoferService.NuevoEvento(java.util.UUID.randomUUID().toString(), guia, tipo,
                "2026-10-12T08:00:00-03:00", null, null, null, null, version);
    }

    // ===== helpers =====

    private Long rutaCalculada() throws Exception {
        return enTx(() -> {
            RutaReferencia ruta = new RutaReferencia("{\"type\":\"LineString\",\"coordinates\":[[-55.55,-30.91],[-56.17,-34.9]]}", 500000);
            em.persist(ruta);
            return ruta.getId();
        });
    }

    @Test
    void elCatalogoSeBuscaSinDistinguirTildes() throws Exception {
        GuiaVista g = enTx(() -> servicio.crear(RESPONSABLE,
                new NuevaGuia("FORESTÁL", "Rollizós", 18000L, MANANA, "Rivera", "Montevideo", null, null, null), "t"));

        assertEquals("Forestal", g.rubroCliente());
        assertEquals("Rollizos", g.tipoCarga());
    }

    @Test
    void unaGuiaAnteriorAlCatalogoSeEditaSinCambiarSuRubroNiSuTipo() throws Exception {
        GuiaVista g = asignada();
        enTx(() -> em.createQuery("UPDATE Guia g SET g.rubroCliente = 'Agro viejo', g.tipoCarga = 'Bolsas' WHERE g.id = :id")
                .setParameter("id", g.id()).executeUpdate());
        em.clear();

        GuiaVista editada = enTx(() -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Agro viejo", "Bolsas", 21000L, MANANA, "Rivera", "Montevideo", null, null,
                        g.version(), null), "t"));

        assertEquals("Agro viejo", editada.rubroCliente());
        assertEquals(21000L, editada.pesoCargaKg());
    }

    @Test
    void alEditarNoSePuedeCambiarAUnValorFueraDelCatalogo() throws Exception {
        GuiaVista g = asignada();

        DatosInvalidosException e = falla(DatosInvalidosException.class, () -> servicio.editar(RESPONSABLE, g.id(),
                new GuiasService.EdicionGuia("Otro", "Rollizos", 21000L, MANANA, "Rivera", "Montevideo", null, null,
                        g.version(), null), "t"));

        assertEquals(Map.of("rubroCliente", "Elegí un rubro de la lista."), e.getErrores());
    }

    private GuiaVista nuevaGuia() throws Exception {
        return enTx(() -> servicio.crear(RESPONSABLE, new NuevaGuia("Forestal", "Rollizos", 20000L, MANANA, "Rivera", "Montevideo", null, null, null), "t"));
    }

    private static Ciudadano validado(String ci, String nombre, String apellido) {
        Ciudadano p = new Ciudadano(ci, null, null);
        p.setEstado(EstadoValidacion.VALIDADO);
        p.setPrimerNombre(nombre);
        p.setPrimerApellido(apellido);
        return p;
    }

    interface Accion<T> {
        T ejecutar() throws Exception;
    }

    private <T> T enTx(Accion<T> accion) throws Exception {
        em.getTransaction().begin();
        try {
            T r = accion.ejecutar();
            em.getTransaction().commit();
            return r;
        } catch (Exception e) {
            em.getTransaction().rollback();
            throw e;
        }
    }

    private <T> T conEm(T dao) throws Exception {
        set(dao, "em", em);
        return dao;
    }

    private static void set(Object destino, String campo, Object valor) throws Exception {
        Field f = destino.getClass().getDeclaredField(campo);
        f.setAccessible(true);
        f.set(destino, valor);
    }
}
