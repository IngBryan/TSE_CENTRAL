package tse.lab.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.io.Serializable;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;
import java.time.Instant;

@Entity
@Table(
    name = "nodos_perifericos",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_nodo_tipo_id_externo",
        columnNames = {"tipo", "id_externo"}
    )
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
    name = "tipo",
    discriminatorType = DiscriminatorType.STRING,
    length = 20
)
public abstract class NodoPeriferico implements Serializable {
    private static final long serialVersionUID = 1L;

    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 200)
    private String ubicacion;

    @Column(name = "id_externo", nullable = false, length = 100)
    private String idExterno;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "ultima_comunicacion_exitosa")
    private Instant ultimaComunicacionExitosa;

    protected NodoPeriferico() {
    }

    protected NodoPeriferico(
        String nombre,
        String ubicacion,
        String idExterno) {

        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.idExterno = idExterno;
        this.activo = true;
    }

    public Long getId() {
        return id;
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


    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(String idExterno) {
        this.idExterno = idExterno;
    }

    public Instant getUltimaComunicacionExitosa() {
        return ultimaComunicacionExitosa;
    }

    public void setUltimaComunicacionExitosa(
            Instant ultimaComunicacionExitosa) {
        this.ultimaComunicacionExitosa = ultimaComunicacionExitosa;
    }

}

