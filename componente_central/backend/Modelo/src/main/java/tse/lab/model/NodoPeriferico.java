package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.io.Serializable;

// id lo genera el central. idExterno es el id del nodo en su sistema (balanza en la API de balanzas, GPS en
// tracking): balanzas y GPS tienen ids que se repiten entre si, por eso es unico por tipo.
@Entity
@Table(name = "nodos_perifericos", uniqueConstraints = @UniqueConstraint(columnNames = { "tipo", "id_externo" }))
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "tipo")
public abstract class NodoPeriferico implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_externo", nullable = false)
    private Long idExterno;

    private String nombre;

    private boolean activo;

    public NodoPeriferico() {
    }

    public NodoPeriferico(Long idExterno, String nombre, boolean activo) {
        this.idExterno = idExterno;
        this.nombre = nombre;
        this.activo = activo;
    }

    public Long getId() {
        return id;
    }

    public Long getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(Long idExterno) {
        this.idExterno = idExterno;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
