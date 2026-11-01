package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "guias")
public class Guia implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String id;
    // Nombre del catalogo Rubro
    @Column(length = 100)
    private String rubroCliente;
    // Nombre del catalogo TipoCarga; lo ve el chofer en la app movil
    @Column(length = 100)
    private String tipoCarga;
    private long pesoCargaKg; /* lo dejo como peso en vez de volumen */
    private LocalDate fecha;
    private String origen;
    private String destino;
    @ManyToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;
    @ManyToOne
    @JoinColumn(name = "id_vehiculo")
    private Vehiculo vehiculo;
    @ManyToOne
    @JoinColumn(name = "id_chofer")
    private Chofer chofer;
    @Enumerated(EnumType.STRING)
    private EstadoGuia estado;
    private Instant fechaAsignacion;
    // Bloqueo optimista: dos asignaciones simultaneas de la misma guia no pueden ganar las dos
    @Version
    private Long version;

    public Guia() {
    }

    public Guia(String id, String rubroCliente, long pesoCargaKg, LocalDate fecha, String origen, String destino,
            Empresa empresa) {
        this.id = id;
        this.rubroCliente = rubroCliente;
        this.pesoCargaKg = pesoCargaKg;
        this.fecha = fecha;
        this.origen = origen;
        this.empresa = empresa;
        this.destino = destino;
        this.vehiculo = null; /* inicialmente no tiene vehiculo asignado */
        this.chofer = null; /* inicialmente no tiene chofer asignado */
        this.estado = EstadoGuia.SIN_ASIGNAR;
    }

    public String getId() {
        return id;
    }

    public String getRubroCliente() {
        return rubroCliente;
    }

    public String getTipoCarga() {
        return tipoCarga;
    }

    public void setTipoCarga(String tipoCarga) {
        this.tipoCarga = tipoCarga;
    }

    public long getPesoCargaKg() {
        return pesoCargaKg;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getOrigen() {
        return origen;
    }

    public String getDestino() {
        return destino;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public Chofer getChofer() {
        return chofer;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setRubroCliente(String rubroCliente) {
        this.rubroCliente = rubroCliente;
    }

    public void setPesoCargaKg(long pesoCargaKg) {
        this.pesoCargaKg = pesoCargaKg;
    }

    public void setFecha(LocalDate fecha) {
        this.fecha = fecha;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;

    }

    public void setChofer(Chofer chofer) {
        this.chofer = chofer;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public EstadoGuia getEstado() {
        return estado;
    }

    public Instant getFechaAsignacion() {
        return fechaAsignacion;
    }

    // CU-07 paso 9: registra la asignacion y actualiza el estado de la guia
    public void asignar(Vehiculo vehiculo, Chofer chofer) {
        this.vehiculo = vehiculo;
        this.chofer = chofer;
        this.estado = EstadoGuia.ASIGNADA;
        this.fechaAsignacion = Instant.now();
    }

    public Long getVersion() {
        return version;
    }

    // App movil: el chofer inicia (ASIGNADA -> EN_CURSO) y finaliza (EN_CURSO -> FINALIZADA) el viaje.
    // Las reglas de transicion se controlan en ViajesChoferService.
    public void cambiarEstado(EstadoGuia nuevo) {
        this.estado = nuevo;
    }
}