package tse.lab.frontofficeciudadanos.service.gestiondeguiasyviajes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Ventana temporal candidata para un GPS: [asignacion, finAplicado).
 * El INICIO aplicado permite priorizar un viaje efectivamente iniciado si hay
 * varias Guías asignadas al mismo vehículo. FINALIZADA sin FIN indica
 * un límite superior desconocido, no una autorización indefinida.
 * Nunca se infieren horas de la fecha
 * prevista ni se toma la hora de recepción de un evento móvil como la del viaje.
 * Clase Serializable (no record) para el cliente EJB remoto de WildFly.
 */
public final class IntervaloGuiaGps implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String idGuia;
    private final String matricula;
    private final String estado;
    private final Instant asignacion;
    private final Instant inicioAplicado;
    private final Instant finAplicado;

    public IntervaloGuiaGps(String idGuia, String matricula, String estado, Instant asignacion,
            Instant inicioAplicado, Instant finAplicado) {
        this.idGuia = Objects.requireNonNull(idGuia, "idGuia");
        this.matricula = Objects.requireNonNull(matricula, "matricula");
        this.estado = Objects.requireNonNull(estado, "estado");
        this.asignacion = Objects.requireNonNull(asignacion, "asignacion");
        this.inicioAplicado = inicioAplicado;
        this.finAplicado = finAplicado;
    }

    public String getIdGuia() { return idGuia; }
    public String getMatricula() { return matricula; }
    public String getEstado() { return estado; }
    public Instant getAsignacion() { return asignacion; }
    public Instant getInicioAplicado() { return inicioAplicado; }
    public Instant getFinAplicado() { return finAplicado; }
}
