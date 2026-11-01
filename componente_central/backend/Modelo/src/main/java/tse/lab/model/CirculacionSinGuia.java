package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** Una circulación observada tiene expediente propio; el GPS íntegro sigue en MongoDB. */
@Entity
@Table(name = "circulaciones_sin_guia")
public class CirculacionSinGuia implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "id_vehiculo", nullable = false)
    private Vehiculo vehiculo;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", unique = true)
    private CasoFiscalizacion caso;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private EstadoCirculacionSinGuia estado;
    @Enumerated(EnumType.STRING)
    private MotivoFinCirculacionSinGuia motivoFin;
    @Column(nullable = false)
    private Instant fechaInicio;
    private Instant fechaFin;
    @Column(nullable = false)
    private Instant ultimaMedicion;
    @Column(nullable = false)
    private Instant ultimaRecepcion;
    @Column(nullable = false)
    private Instant ultimoMovimiento;
    private double latitudActual;
    private double longitudActual;
    private double latitudMovimiento;
    private double longitudMovimiento;

    protected CirculacionSinGuia() { }

    public CirculacionSinGuia(Vehiculo vehiculo, Instant fecha, double lat, double lon, Instant recepcion) {
        this.vehiculo = vehiculo;
        this.fechaInicio = fecha;
        this.ultimaMedicion = fecha;
        this.ultimoMovimiento = fecha;
        this.ultimaRecepcion = recepcion;
        this.latitudActual = this.latitudMovimiento = lat;
        this.longitudActual = this.longitudMovimiento = lon;
        this.estado = EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO;
    }
    public void reiniciarPendiente(Instant fecha, double lat, double lon, Instant recepcion) {
        if (estado != EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO) throw new IllegalStateException();
        this.fechaInicio = fecha;
        actualizar(fecha, lat, lon, recepcion, true);
    }
    public void confirmar(CasoFiscalizacion caso, Instant fecha, double lat, double lon, Instant recepcion) {
        if (estado != EstadoCirculacionSinGuia.PENDIENTE_MOVIMIENTO || caso == null)
            throw new IllegalStateException("No se puede confirmar la circulación");
        this.caso = caso;
        this.estado = EstadoCirculacionSinGuia.ACTIVA;
        actualizar(fecha, lat, lon, recepcion, true);
    }
    public void actualizar(Instant fecha, double lat, double lon, Instant recepcion, boolean movimiento) {
        if (fecha.isBefore(ultimaMedicion)) throw new IllegalArgumentException("GPS fuera de orden");
        ultimaMedicion = fecha;
        ultimaRecepcion = recepcion;
        latitudActual = lat;
        longitudActual = lon;
        if (movimiento) {
            ultimoMovimiento = fecha;
            latitudMovimiento = lat;
            longitudMovimiento = lon;
        }
    }
    public void finalizar(MotivoFinCirculacionSinGuia motivo, Instant fin) {
        if (estado == EstadoCirculacionSinGuia.FINALIZADA) return;
        if (fin.isBefore(fechaInicio)) throw new IllegalArgumentException("Fin anterior al inicio");
        this.estado = EstadoCirculacionSinGuia.FINALIZADA;
        this.motivoFin = motivo;
        this.fechaFin = fin;
    }
    public Long getId() { return id; }
    public Vehiculo getVehiculo() { return vehiculo; }
    public CasoFiscalizacion getCaso() { return caso; }
    public EstadoCirculacionSinGuia getEstado() { return estado; }
    public MotivoFinCirculacionSinGuia getMotivoFin() { return motivoFin; }
    public Instant getFechaInicio() { return fechaInicio; }
    public Instant getFechaFin() { return fechaFin; }
    public Instant getUltimaMedicion() { return ultimaMedicion; }
    public Instant getUltimaRecepcion() { return ultimaRecepcion; }
    public Instant getUltimoMovimiento() { return ultimoMovimiento; }
    public double getLatitudActual() { return latitudActual; }
    public double getLongitudActual() { return longitudActual; }
    public double getLatitudMovimiento() { return latitudMovimiento; }
    public double getLongitudMovimiento() { return longitudMovimiento; }
}
