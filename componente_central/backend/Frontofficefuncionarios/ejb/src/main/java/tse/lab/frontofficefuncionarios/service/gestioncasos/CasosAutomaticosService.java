package tse.lab.frontofficefuncionarios.service.gestioncasos;

import java.time.Duration;
import java.time.Instant;
import jakarta.ejb.Schedule;
import tse.lab.frontofficefuncionarios.data.CirculacionSinGuiaDAO;
import tse.lab.model.CirculacionSinGuia;
import tse.lab.model.EstadoCirculacionSinGuia;
import tse.lab.model.MotivoFinCirculacionSinGuia;
import java.util.List;
import java.util.logging.Logger;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import tse.lab.frontofficefuncionarios.data.AuditoriaCasoDAO;
import tse.lab.frontofficefuncionarios.data.CasoFiscalizacionDAO;
import tse.lab.frontofficefuncionarios.service.LogFuncionarios;
import tse.lab.model.AccionAuditoriaCaso;
import tse.lab.model.CasoFiscalizacion;
import tse.lab.model.ClaveEvento;
import tse.lab.model.EstadoCaso;
import tse.lab.model.EvidenciaGeometricaDesvio;
import tse.lab.model.EventoPesajes;
import tse.lab.model.Guia;
import tse.lab.model.RegistroAuditoriaCaso;
import tse.lab.model.Vehiculo;
import tse.lab.model.EvidenciaHabilitacion;

// La fiscalización decide las infracciones; este EJB asocia evidencias en PostgreSQL
// en una sola transacción por caso y evento. Una misma observación puede respaldar varios casos.
@Stateless
public class CasosAutomaticosService implements CasosAutomaticosRemote {

    private static final Logger LOG = Logger.getLogger(CasosAutomaticosService.class.getName());

    @EJB
    private CasoFiscalizacionDAO casos;
    @EJB
    private AuditoriaCasoDAO auditoria;
    @EJB
    private CirculacionSinGuiaDAO circulaciones;

    // Parámetros de observación del prototipo, NO definiciones legales.
    static final Duration VENTANA_INICIO = Duration.ofSeconds(180);
    static final Duration DETENCION = Duration.ofMinutes(30);
    static final Duration SIN_SENAL = Duration.ofMinutes(45);
    static final double MIN_DESPLAZAMIENTO_M = 100;
    static final double MAX_VELOCIDAD_KMH = 160;

    @Override
    public List<DatosCaso> casosAbiertos(String matricula) {
        return casos.abiertosDelVehiculo(matricula).stream().map(CasosAutomaticosService::datos).toList();
    }

    @Override
    public DatosCaso registrarEvidencia(TipoCasoAutomatico tipoCaso, String idGuia, EventoFiscalizado evento) {
        if (tipoCaso == TipoCasoAutomatico.VEHICULO_SIN_HABILITACION) {
            throw new IllegalArgumentException("La habilitación requiere su prueba documental");
        }
        if (tipoCaso == TipoCasoAutomatico.DESVIO_RUTA) {
            throw new IllegalArgumentException("El desvío requiere su prueba geométrica");
        }
        if (tipoCaso == TipoCasoAutomatico.VIAJE_SIN_GUIA) {
            throw new IllegalArgumentException("Usar registrarCirculacionSinGuia: requiere movimiento GPS confirmado");
        }
        return registrarInterno(tipoCaso, idGuia, evento, null);
    }

    @Override
    public DatosCaso registrarHabilitacion(String idGuia, EventoFiscalizado evento,
            EvidenciaHabilitacion prueba) {
        if (prueba == null || evento == null || evento.getOrigen() == null
                || !prueba.getMatricula().equals(evento.getMatricula())
                || !prueba.getFechaHora().equals(evento.getFechaHora()) || !prueba.sinHabilitacion()) {
            throw new IllegalArgumentException("La prueba documental no acredita la infracción de este evento");
        }
        return registrarInterno(TipoCasoAutomatico.VEHICULO_SIN_HABILITACION, idGuia, evento, null,
                prueba.conOrigen(evento.getOrigen().name()));
    }

    @Override
    public DatosCaso registrarDesvio(String idGuia, EventoFiscalizado evento, PruebaDesvioRemota prueba) {
        validarPrueba(idGuia, evento, prueba);
        return registrarInterno(TipoCasoAutomatico.DESVIO_RUTA, idGuia, evento, prueba);
    }

    private DatosCaso registrarInterno(TipoCasoAutomatico tipoCaso, String idGuia,
            EventoFiscalizado evento, PruebaDesvioRemota prueba) {
        return registrarInterno(tipoCaso, idGuia, evento, prueba, null);
    }

    private DatosCaso registrarInterno(TipoCasoAutomatico tipoCaso, String idGuia,
            EventoFiscalizado evento, PruebaDesvioRemota prueba,
            EvidenciaHabilitacion habilitacion) {
        if (tipoCaso == null || evento == null || evento.getOrigen() == null
                || evento.getMatricula() == null || evento.getFechaHora() == null) {
            throw new IllegalArgumentException("Faltan datos del tipo o del evento");
        }
        if (tipoCaso == TipoCasoAutomatico.DESVIO_RUTA && idGuia == null) {
            throw new IllegalArgumentException("Un desvío necesita Guía");
        }
        if (tipoCaso == TipoCasoAutomatico.VIAJE_SIN_GUIA && idGuia != null) {
            throw new IllegalArgumentException("Viaje sin Guía no debe asociarse a una Guía");
        }
        if (evento.getRevisionReglas()!=null && evento.getRevisionReglas()<1)
            throw new IllegalArgumentException("Revisión de reglas inválida");
        Vehiculo vehiculo = casos.buscarVehiculo(evento.getMatricula());
        if (vehiculo == null) {
            throw new IllegalArgumentException("El vehículo no existe: " + evento.getMatricula());
        }
        Guia guia = idGuia == null ? null : casos.buscarGuia(idGuia);
        if (idGuia != null && guia == null) {
            throw new IllegalArgumentException("La Guía no existe: " + idGuia);
        }
        if (guia != null && (guia.getVehiculo() == null
                || !evento.getMatricula().equals(guia.getVehiculo().getMatricula()))) {
            throw new IllegalArgumentException("La Guía no corresponde al vehículo: " + idGuia);
        }
        // Si el mismo evento ya respaldó este tipo/contexto, el reintento nunca debe
        // reabrir otro caso, aunque el original ahora esté EN_DESCARGO o resuelto.
        CasoFiscalizacion asociado = casos.buscarConEvidencia(evento.getMatricula(), idGuia,
                tipoCaso.name(), clave(evento), evento.getOrigen() == OrigenEvento.TRACKING);
        if (asociado != null) {
            if (prueba != null || habilitacion != null || evento.getRevisionReglas()!=null) {
                asociado = casos.buscarPorIdParaActualizar(asociado.getId());
                if (prueba != null) agregarPruebaSiFalta(asociado, evento, prueba);
                if (habilitacion != null) agregarHabilitacionSiFalta(asociado, habilitacion);
            }
            agregarReglasSiFalta(asociado, evento);
            return datos(asociado);
        }
        CasoFiscalizacion caso = casos.buscarAbierto(evento.getMatricula(), idGuia, tipoCaso.name());
        if (caso == null) {
            caso = new CasoFiscalizacion(tipoCaso.getDescripcion(), tipoCaso.name(), vehiculo, guia);
            asociarSiFalta(caso, evento);
            agregarReglasSiFalta(caso, evento);
            if (prueba != null) agregarPruebaSiFalta(caso, evento, prueba);
            if (habilitacion != null) agregarHabilitacionSiFalta(caso, habilitacion);
            casos.guardar(caso);
            auditoria.registrar(new RegistroAuditoriaCaso(caso.getId(), RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CREACION, null, EstadoCaso.DETECTADA,
                    "Detectado por fiscalización automática en evento " + evento.getOrigen() + " del "
                            + evento.getFechaHora(), evento.getCorrelationId()));
            Long id = caso.getId();
            LOG.info(() -> LogFuncionarios.evento("caso.creado").correlationId(evento.getCorrelationId())
                    .campo("caso", id).campo("tipo", tipoCaso).campo("matricula", evento.getMatricula())
                    .campo("fechaHora", evento.getFechaHora()).toString());
        } else {
            caso = casos.buscarPorIdParaActualizar(caso.getId());
            asociarSiFalta(caso, evento);
            agregarReglasSiFalta(caso, evento);
            if (prueba != null) agregarPruebaSiFalta(caso, evento, prueba);
            if (habilitacion != null) agregarHabilitacionSiFalta(caso, habilitacion);
            Long id = caso.getId();
            LOG.info(() -> LogFuncionarios.evento("caso.evidencia_agregada")
                    .correlationId(evento.getCorrelationId()).campo("caso", id)
                    .campo("matricula", evento.getMatricula()).campo("fechaHora", evento.getFechaHora()).toString());
        }
        return datos(caso);
    }

    private static void agregarReglasSiFalta(CasoFiscalizacion caso, EventoFiscalizado e) {
        if (e.getRevisionReglas()==null) return; // compatibilidad con expedientes anteriores
        boolean existe=caso.getEvidenciasReglas().stream().anyMatch(r ->
                r.corresponde(e.getMatricula(), e.getFechaHora(), e.getOrigen().name()));
        if (!existe) caso.getEvidenciasReglas().add(new tse.lab.model.EvidenciaReglas(
                e.getMatricula(), e.getFechaHora(), e.getOrigen().name(), e.getRevisionReglas(), e.getPesoBaseKg()));
    }

    private static void agregarHabilitacionSiFalta(CasoFiscalizacion caso,
            EvidenciaHabilitacion prueba) {
        boolean existe = caso.getEvidenciasHabilitacion().stream().anyMatch(p ->
                p.getFechaHora().equals(prueba.getFechaHora()) && p.getOrigen().equals(prueba.getOrigen())
                        && p.getMatricula().equals(prueba.getMatricula()));
        if (!existe) caso.getEvidenciasHabilitacion().add(prueba);
    }


    /**
     * Gestiona una única circulación por matrícula bajo bloqueo de la fila Vehiculo.
     * El primer GPS es una observación pendiente; el segundo debe confirmar movimiento.
     * Casos y claves GPS se escriben atómicamente en PostgreSQL.
     */
    @Override
    public DatosCaso registrarCirculacionSinGuia(PosicionSinGuiaRemota gps) {
        if (gps == null || gps.getMatricula() == null || gps.getMatricula().isBlank()
                || gps.getFechaHora() == null || !coordenadasValidas(gps.getLatitud(), gps.getLongitud())) {
            throw new IllegalArgumentException("Posición GPS sin Guía inválida");
        }
        Vehiculo vehiculo = circulaciones.bloquearVehiculo(gps.getMatricula());
        if (vehiculo == null) throw new IllegalArgumentException("Vehículo inexistente");
        Instant ahora = Instant.now();
        var clave = new ClaveEvento(gps.getMatricula(), gps.getFechaHora());
        CasoFiscalizacion yaRegistrado = casos.buscarConEvidencia(gps.getMatricula(), null,
                TipoCasoAutomatico.VIAJE_SIN_GUIA.name(), clave, true);
        if (yaRegistrado != null) return datos(yaRegistrado);
        CirculacionSinGuia actual = circulaciones.actual(gps.getMatricula());
        // Una muestra retrasada solo se incorpora si pertenece inequívocamente a un período observado.
        if (actual == null || gps.getFechaHora().isBefore(actual.getFechaInicio())
                || gps.getFechaHora().isBefore(actual.getUltimaMedicion())) {
            var historica = circulaciones.porFecha(gps.getMatricula(), gps.getFechaHora());
            if (historica != null) {
                asociarSiFalta(historica.getCaso(), evento(gps));
                return datos(historica.getCaso());
            }
            if (actual != null || existeHistorialPosterior(gps.getMatricula(), gps.getFechaHora())) {
                // No crear un episodio artificial con GPS atrasados y contexto incompleto.
                throw new IllegalArgumentException("GPS atrasado sin circulación identificable; requiere conciliación");
            }
        }
        if (actual == null) {
            circulaciones.guardar(new CirculacionSinGuia(vehiculo, gps.getFechaHora(),
                    gps.getLatitud(), gps.getLongitud(), ahora));
            return null;
        }
        if (gps.getFechaHora().equals(actual.getUltimaMedicion())) return null;
        Duration intervalo = Duration.between(actual.getUltimaMedicion(), gps.getFechaHora());
        if (intervalo.compareTo(SIN_SENAL) > 0) {
            actual.finalizar(MotivoFinCirculacionSinGuia.BRECHA_TEMPORAL, actual.getUltimaMedicion());
            circulaciones.guardar(new CirculacionSinGuia(vehiculo, gps.getFechaHora(),
                    gps.getLatitud(), gps.getLongitud(), ahora));
            return null;
        }
        if (actual.getEstado() == EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO) {
            double distancia = distanciaMetros(actual.getLatitudActual(), actual.getLongitudActual(),
                    gps.getLatitud(), gps.getLongitud());
            double horas = intervalo.toMillis() / 3_600_000.0;
            if (intervalo.compareTo(VENTANA_INICIO) > 0 || horas <= 0
                    || distancia / 1000 / horas > MAX_VELOCIDAD_KMH) {
                actual.reiniciarPendiente(gps.getFechaHora(), gps.getLatitud(), gps.getLongitud(), ahora);
                return null;
            }
            if (distancia < MIN_DESPLAZAMIENTO_M) {
                // Descartar pequeñas variaciones estacionarias sin inventar un viaje.
                actual.reiniciarPendiente(gps.getFechaHora(), gps.getLatitud(), gps.getLongitud(), ahora);
                return null;
            }
            CasoFiscalizacion caso = new CasoFiscalizacion(TipoCasoAutomatico.VIAJE_SIN_GUIA.getDescripcion(),
                    TipoCasoAutomatico.VIAJE_SIN_GUIA.name(), vehiculo, null);
            caso.getClavesEventosTracking().add(new ClaveEvento(gps.getMatricula(), actual.getFechaInicio()));
            asociarSiFalta(caso, evento(gps));
            casos.guardar(caso);
            casos.flush();
            actual.confirmar(caso, gps.getFechaHora(), gps.getLatitud(), gps.getLongitud(), ahora);
            auditoria.registrar(new RegistroAuditoriaCaso(caso.getId(), RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CREACION, null, EstadoCaso.DETECTADA,
                    "Circulación sin Guía inferida por desplazamiento GPS entre "
                            + actual.getFechaInicio() + " y " + gps.getFechaHora(), null));
            LOG.info(() -> LogFuncionarios.evento("circulacion.confirmada")
                    .campo("caso", caso.getId()).campo("matricula", gps.getMatricula())
                    .campo("fechaHora", gps.getFechaHora()).toString());
            return datos(caso);
        }
        // Un salto incompatible con el transporte se conserva en Mongo, pero no se
        // agrega como trayectoria probatoria de esta circulación.
        double desplazamiento = distanciaMetros(actual.getLatitudActual(), actual.getLongitudActual(),
                gps.getLatitud(), gps.getLongitud());
        double horasTramo = intervalo.toMillis() / 3_600_000.0;
        if (horasTramo <= 0 || desplazamiento / 1000 / horasTramo > MAX_VELOCIDAD_KMH) return null;
        double distanciaDesdeMovimiento = distanciaMetros(actual.getLatitudMovimiento(),
                actual.getLongitudMovimiento(), gps.getLatitud(), gps.getLongitud());
        if (Duration.between(actual.getUltimoMovimiento(), gps.getFechaHora()).compareTo(DETENCION) >= 0
                && distanciaDesdeMovimiento < MIN_DESPLAZAMIENTO_M) {
            actual.finalizar(MotivoFinCirculacionSinGuia.DETENCION_PROLONGADA, actual.getUltimoMovimiento());
            circulaciones.guardar(new CirculacionSinGuia(vehiculo, gps.getFechaHora(),
                    gps.getLatitud(), gps.getLongitud(), ahora));
            return null;
        }
        asociarSiFalta(actual.getCaso(), evento(gps));
        actual.actualizar(gps.getFechaHora(), gps.getLatitud(), gps.getLongitud(), ahora,
                distanciaDesdeMovimiento >= MIN_DESPLAZAMIENTO_M);
        return datos(actual.getCaso());
    }

    @Override
    public void cerrarCirculacionPorGuia(String matricula, Instant fechaInicioGuia) {
        if (matricula == null || fechaInicioGuia == null) throw new IllegalArgumentException("Contexto inválido");
        if (circulaciones.bloquearVehiculo(matricula) == null) return;
        CirculacionSinGuia actual = circulaciones.actual(matricula);
        if (actual == null) return;
        if (fechaInicioGuia.isBefore(actual.getUltimaMedicion())) {
            // Los eventos móviles atrasados pueden invalidar la clasificación: no falsificar un cierre.
            throw new IllegalStateException("Guía superpuesta con una circulación: requiere conciliación");
        }
        actual.finalizar(MotivoFinCirculacionSinGuia.GUIA_INICIADA, actual.getUltimaMedicion());
    }

    /** Inactividad de recepción: inferimos el fin en la última medición, no en la hora del cron. */
    @Schedule(hour = "*", minute = "*/5", persistent = false)
    public void cerrarSinSenal() {
        Instant limite = Instant.now().minus(SIN_SENAL);
        for (String matricula : circulaciones.matriculasInactivas(limite, 100)) {
            if (circulaciones.bloquearVehiculo(matricula) == null) continue;
            CirculacionSinGuia actual = circulaciones.actual(matricula);
            if (actual != null && actual.getUltimaRecepcion().isBefore(limite)) {
                actual.finalizar(MotivoFinCirculacionSinGuia.SIN_SENAL_GPS, actual.getUltimaMedicion());
            }
        }
    }

    private boolean existeHistorialPosterior(String matricula, Instant fecha) {
        // La existencia de cualquier episodio posterior impide iniciar uno retrospectivo sin reconciliación.
        return circulaciones.hayEpisodioPosterior(matricula, fecha);
    }

    private static EventoFiscalizado evento(PosicionSinGuiaRemota gps) {
        return new EventoFiscalizado(gps.getMatricula(), gps.getFechaHora(), OrigenEvento.TRACKING);
    }
    private static double distanciaMetros(double aLat, double aLon, double bLat, double bLon) {
        double dLat = Math.toRadians(bLat - aLat), dLon = Math.toRadians(bLon - aLon);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(Math.toRadians(aLat))
                * Math.cos(Math.toRadians(bLat)) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * 6_371_000 * Math.asin(Math.sqrt(Math.min(1, Math.max(0, h))));
    }

    private static void validarPrueba(String idGuia, EventoFiscalizado evento, PruebaDesvioRemota p) {
        if (idGuia == null || idGuia.isBlank() || evento == null || evento.getFechaHora() == null
                || evento.getMatricula() == null || evento.getOrigen() != OrigenEvento.TRACKING || p == null
                || p.getInstantePareja() == null || p.getHashesRutas() == null
                || p.getHashesRutas().isEmpty() || p.getHashesRutas().size() > 3) {
            throw new IllegalArgumentException("Prueba de desvío incompleta");
        }
        var separacion = Duration.between(evento.getFechaHora(), p.getInstantePareja()).abs();
        if (separacion.isZero() || p.getVentanaSegundos() <= 0
                || separacion.compareTo(Duration.ofSeconds(p.getVentanaSegundos())) > 0
                || !Double.isFinite(p.getUmbralMetros()) || p.getUmbralMetros() <= 0
                || !Double.isFinite(p.getDistanciaConfirmante()) || !Double.isFinite(p.getDistanciaPareja())
                || p.getDistanciaConfirmante() <= p.getUmbralMetros()
                || p.getDistanciaPareja() <= p.getUmbralMetros()
                || !coordenadasValidas(p.getLatConfirmante(), p.getLonConfirmante())
                || !coordenadasValidas(p.getLatPareja(), p.getLonPareja())
                || p.getHashesRutas().stream().anyMatch(h -> h == null || h.isBlank()
                        || h.contains(",") || h.length() > 64)
                || String.join(",", p.getHashesRutas()).length() > 256) {
            throw new IllegalArgumentException("Valores geométricos de desvío inválidos");
        }
    }

    private static boolean coordenadasValidas(double lat, double lon) {
        return Double.isFinite(lat) && Double.isFinite(lon) && lat >= -90 && lat <= 90
                && lon >= -180 && lon <= 180;
    }

    private static void agregarPruebaSiFalta(CasoFiscalizacion caso, EventoFiscalizado evento,
            PruebaDesvioRemota prueba) {
        if (caso.getEvidenciasDesvio().stream().anyMatch(e -> e.getMatricula().equals(evento.getMatricula())
                && e.getFechaHora().equals(evento.getFechaHora()))) return;
        caso.getEvidenciasDesvio().add(new EvidenciaGeometricaDesvio(evento.getMatricula(),
                evento.getFechaHora(), prueba.getInstantePareja(),
                prueba.getLatConfirmante(), prueba.getLonConfirmante(),
                prueba.getLatPareja(), prueba.getLonPareja(),
                prueba.getDistanciaConfirmante(), prueba.getDistanciaPareja(),
                prueba.getUmbralMetros(), prueba.getVentanaSegundos(),
                String.join(",", prueba.getHashesRutas())));
    }

    @Override
    public DatosCaso notificarCaso(long idCaso, String correlationId) {
        CasoFiscalizacion caso = casos.buscarPorIdParaActualizar(idCaso);
        if (caso == null) {
            throw new IllegalArgumentException("El caso no existe: " + idCaso);
        }
        if (caso.getEstado() == EstadoCaso.DETECTADA) {
            caso.cambiarEstado(EstadoCaso.NOTIFICADA);
            Long nroEmpresa = caso.getVehiculo() == null || caso.getVehiculo().getEmpresa() == null ? null
                    : caso.getVehiculo().getEmpresa().getNroEmpresa();
            auditoria.registrar(new RegistroAuditoriaCaso(idCaso, RegistroAuditoriaCaso.SISTEMA,
                    AccionAuditoriaCaso.CAMBIO_ESTADO, EstadoCaso.DETECTADA, EstadoCaso.NOTIFICADA,
                    "Caso notificado a la empresa N° " + nroEmpresa, correlationId));
            LOG.info(() -> LogFuncionarios.evento("caso.notificado").correlationId(correlationId).campo("caso", idCaso)
                    .campo("empresa", nroEmpresa).toString());
        }
        return datos(caso);
    }

    /** El bloqueo de caso y la unicidad (id_caso, clave) garantizan idempotencia. */
    private void asociarSiFalta(CasoFiscalizacion caso, EventoFiscalizado evento) {
        ClaveEvento clave = clave(evento);
        if (evento.getOrigen() == OrigenEvento.TRACKING) {
            if (!caso.getClavesEventosTracking().contains(clave)) {
                caso.getClavesEventosTracking().add(clave);
            }
            return;
        }
        EventoPesajes pesaje = casos.buscarPesaje(clave);
        if (pesaje == null) {
            throw new IllegalArgumentException("El pesaje no está guardado: " + clave.getMatricula()
                    + " " + clave.getFechaHora());
        }
        if (!caso.getEventosPesajes().stream().anyMatch(p -> p.getMatricula().equals(clave.getMatricula())
                && p.getFechaHora().equals(clave.getFechaHora()))) {
            caso.getEventosPesajes().add(pesaje);
        }
    }

    private static ClaveEvento clave(EventoFiscalizado evento) {
        return new ClaveEvento(evento.getMatricula(), evento.getFechaHora());
    }

    private static DatosCaso datos(CasoFiscalizacion caso) {
        return new DatosCaso(caso.getId(), caso.getTipoCaso(), caso.getEstado().name());
    }
}
