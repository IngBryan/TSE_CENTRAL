package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Rubro de actividad de una empresa transportista (agropecuario, forestal...). Es un catalogo precargado del
// sistema (esquema-adicional.sql): no tiene ABM. Una empresa puede tener varios.
@Entity
@Table(name = "rubros")
public class Rubro implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String nombre;

    public Rubro() {
    }

    public Rubro(Long id, String nombre) {
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
