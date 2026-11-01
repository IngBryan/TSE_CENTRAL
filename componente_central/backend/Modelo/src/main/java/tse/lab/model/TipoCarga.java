package tse.lab.model;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Catalogo precargado (esquema-adicional.sql): la letra pide que no tenga ABM. El nombre es la clave:
// lo referencia Guia.tipoCarga.
@Entity
@Table(name = "tipos_carga")
public class TipoCarga implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @Column(length = 100)
    private String nombre;

    public TipoCarga() {
    }

    public TipoCarga(String nombre) {
        this.nombre = nombre;
    }

    public String getNombre() {
        return nombre;
    }
}
