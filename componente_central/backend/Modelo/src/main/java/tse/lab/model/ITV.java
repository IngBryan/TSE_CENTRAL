package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "itv")
public class ITV implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    @ManyToOne(optional = false)
    @JoinColumn(name = "matricula", nullable = false)
    private Vehiculo vehiculo;

    public ITV() {
    }

    // ABM de vehiculos del frontoffice de ciudadanos: la empresa solo declara el vencimiento de la ITV
    public ITV(LocalDate fechaVencimiento, Vehiculo vehiculo) {
        this(null, fechaVencimiento, vehiculo);
    }

    public ITV(LocalDate fechaEmision, LocalDate fechaVencimiento, Vehiculo vehiculo) {
        this.fechaEmision = fechaEmision;
        this.fechaVencimiento = fechaVencimiento;
        this.vehiculo = vehiculo;
    }

    public Long getId() {
        return id;
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

    public Vehiculo getVehiculo() {
        return vehiculo;
    }

    public void setVehiculo(Vehiculo vehiculo) {
        this.vehiculo = vehiculo;
    }
}

