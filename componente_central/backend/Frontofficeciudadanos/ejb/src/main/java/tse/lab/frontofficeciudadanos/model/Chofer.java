package tse.lab.frontofficeciudadanos.model;

import java.io.Serializable;
import java.time.Instant;

import jakarta.persistence.Column;
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

// Asociacion de una persona (por cedula) como chofer de una empresa. Una persona puede ser chofer
// de varias empresas (CU-02), por eso la clave es un id propio y (ci, empresa) es unico. Las guias
// (CU-07) referencian esta asociacion, asi el chofer asignado siempre es de la empresa de la guia.
@Entity
@Table(name = "choferes", uniqueConstraints = @UniqueConstraint(columnNames = { "ci", "nro_empresa" }))
public class Chofer implements Serializable, Ciudadano {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String ci;
    private String email;
    @ManyToOne
    @JoinColumn(name = "nro_empresa")
    private Empresa empresa;
    @Enumerated(EnumType.STRING)
    private EstadoChofer estado;
    private Instant fechaAlta;

    public Chofer() {
    }

    public Chofer(String ci, String email, Empresa empresa) {
        this(ci, email, empresa, EstadoChofer.HABILITADO);
    }

    public Chofer(String ci, String email, Empresa empresa, EstadoChofer estado) {
        this.ci = ci;
        this.email = email;
        this.empresa = empresa;
        this.estado = estado;
        this.fechaAlta = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCi() {
        return ci;
    }

    public Empresa getEmpresa() {
        return empresa;
    }

    public void setCi(String ci) {
        this.ci = ci;
    }

    public String getEmail() {
        return email;
    }

    public void setEmpresa(Empresa empresa) {
        this.empresa = empresa;
    }

    public void setEmail(String email) {
        this.email = email;
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
}
