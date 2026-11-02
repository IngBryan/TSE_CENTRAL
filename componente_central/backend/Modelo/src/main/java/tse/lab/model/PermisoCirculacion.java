package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;

// Un vehiculo puede tener a lo sumo un permiso; la relacion se guarda aqui sin modificar Vehiculo.
@Entity
@Table(name = "permisos_circulacion")
public class PermisoCirculacion implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private String nro;

    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    @OneToOne(optional = false)
    @JoinColumn(name = "matricula", nullable = false, unique = true)
    private Vehiculo vehiculo;

    public PermisoCirculacion() {
    }

    public PermisoCirculacion(String nro, LocalDate fechaEmision, LocalDate fechaVencimiento, Vehiculo vehiculo) {
        this.nro = nro;
        this.fechaEmision = fechaEmision;
        this.fechaVencimiento = fechaVencimiento;
        this.vehiculo = vehiculo;
    }

    public String getNro() {
        return nro;
    }

    public void setNro(String nro) {
        this.nro = nro;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public boolean vigenteEn(LocalDate fecha) {
        return fechaEmision != null && fechaVencimiento != null
                && !fecha.isBefore(fechaEmision) && !fecha.isAfter(fechaVencimiento);
    }

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }
}
