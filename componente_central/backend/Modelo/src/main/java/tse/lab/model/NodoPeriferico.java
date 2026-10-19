
package tse.lab.model;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.io.Serializable;
import java.time.Instant;

// id: identificador interno generado por el componente central.
// idExterno: identificador del nodo en su sistema de origen.
// Balanzas y Tracking tienen espacios de identificación independientes,
// por lo que idExterno debe ser único dentro de cada tipo.
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

    @Column(name = "id_externo", nullable = false)
    private Long idExterno;

    @Column(nullable = false)
    private boolean activo;

    @Column(name = "ultima_comunicacion_exitosa")
    private Instant ultimaComunicacionExitosa;

    protected NodoPeriferico() {
    }

    protected NodoPeriferico(
            String nombre,
            String ubicacion,
            Long idExterno) {

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

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public Long getIdExterno() {
        return idExterno;
    }

    public void setIdExterno(Long idExterno) {
        this.idExterno = idExterno;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Instant getUltimaComunicacionExitosa() {
        return ultimaComunicacionExitosa;
    }

    public void setUltimaComunicacionExitosa(
            Instant ultimaComunicacionExitosa) {
        this.ultimaComunicacionExitosa = ultimaComunicacionExitosa;
    }
}
