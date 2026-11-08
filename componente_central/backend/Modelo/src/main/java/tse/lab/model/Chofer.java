package tse.lab.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Rol de un ciudadano como chofer de una empresa. Una persona puede ser chofer de varias empresas
// (CU-02), por eso la clave es un id propio y (ci, empresa) es unico. Los datos de la persona estan en
// Ciudadano. Las guias (CU-07) referencian esta asociacion, asi el chofer asignado siempre es de la
// empresa de la guia.
@Entity
@Table(name = "choferes", uniqueConstraints = @UniqueConstraint(columnNames = { "ci", "nro_empresa" }))
public class Chofer implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "ci", nullable = false)
    private Ciudadano ciudadano;
    @ManyToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;
    @Enumerated(EnumType.STRING)
    private EstadoChofer estado;
    private Instant fechaAlta;

    public Chofer() {
    }

    public Chofer(Ciudadano ciudadano, Empresa empresa) {
        this(ciudadano, empresa, EstadoChofer.HABILITADO);
    }

    public Chofer(Ciudadano ciudadano, Empresa empresa, EstadoChofer estado) {
        this.ciudadano = ciudadano;
        this.empresa = empresa;
        this.estado = estado;
        this.fechaAlta = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public String getCi() {
        return ciudadano == null ? null : ciudadano.getCi();
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public EstadoChofer getEstado() {
        return estado;
    }

    public void setEstado(EstadoChofer estado) {
        this.estado = estado;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }

    public boolean isDadoDeBaja() {
        return estado == EstadoChofer.DADO_DE_BAJA;
    }

    // Vuelve a dar de alta en la empresa a un chofer dado de baja: nuevo estado y nueva fecha de alta
    public void reactivar(EstadoChofer nuevo) {
        this.estado = nuevo;
        this.fechaAlta = Instant.now();
    }
}
