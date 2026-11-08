package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Tipo de carga de una guia (granos a granel, rollizos...). Catalogo precargado del sistema
// (esquema-adicional.sql), sin ABM: al crear o editar una guia se elige uno de la lista.
@Entity
@Table(name = "tipos_carga")
public class TipoCarga implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    public TipoCarga() {
    }

    public TipoCarga(Long id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }
}
