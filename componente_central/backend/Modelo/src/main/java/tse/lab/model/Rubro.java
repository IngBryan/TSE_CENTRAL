package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Catalogo precargado (esquema-adicional.sql): la letra pide que no tenga ABM. El nombre es la clave:
// lo referencian Guia.rubroCliente (rubro del cliente) y Empresa.rubro.
@Entity
@Table(name = "rubros")
public class Rubro implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 100)
    private String nombre;

    public Rubro() {
    }

    public Rubro(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
