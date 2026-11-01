package tse.lab.model;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Un permiso a demanda (TipoPermiso) cargado por el responsable para un vehiculo: uno por tipo y vehiculo.
// numero y fechaVencimiento solo se piden si el tipo los pide; sin vencimiento, el permiso no vence.
@Entity
@Table(name = "permisos_vehiculo", uniqueConstraints = @UniqueConstraint(name = "uk_permiso_vehiculo_tipo",
        columnNames = { "matricula", "id_tipo" }))
public class PermisoVehiculo implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "matricula")
    private Vehiculo vehiculo;
    @ManyToOne(optional = false)
    @JoinColumn(name = "id_tipo")
    private TipoPermiso tipo;
    @Column(length = 30)
    private String numero;
    private LocalDate fechaVencimiento;

    public PermisoVehiculo() {
    }

    public PermisoVehiculo(Vehiculo vehiculo, TipoPermiso tipo, String numero, LocalDate fechaVencimiento) {
        this.vehiculo = vehiculo;
        this.tipo = tipo;
        this.numero = numero;
        this.fechaVencimiento = fechaVencimiento;
    }

    public Long getId() {
        return id;
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public TipoPermiso getTipo() {
        return tipo;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public boolean vigenteAl(LocalDate fecha) {
        return fechaVencimiento == null || !fecha.isAfter(fechaVencimiento);
    }
}
