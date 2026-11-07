package tse.lab.model;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Asociacion que fija las geometrías autorizadas para una Guia concreta. */
@Entity
@Table(name = "guias_rutas_autorizadas", uniqueConstraints = {
        @UniqueConstraint(name = "uk_guia_ruta", columnNames = {"guia_id", "ruta_referencia_id"})
})
public class GuiaRutaAutorizada implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "guia_id", nullable = false, updatable = false)
    private Guia guia;

    // NO cascade: la geometria puede ser compartida por multiples guias.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ruta_referencia_id", nullable = false, updatable = false)
    private RutaReferencia rutaReferencia;

    @Column(name = "numero_alternativa", nullable = false)
    private int numeroAlternativa;

    protected GuiaRutaAutorizada() { }

    public GuiaRutaAutorizada(Guia guia, RutaReferencia rutaReferencia, int numeroAlternativa) {
        this.guia = Objects.requireNonNull(guia, "guia");
        this.rutaReferencia = Objects.requireNonNull(rutaReferencia, "rutaReferencia");
        if (numeroAlternativa < 1) {
            throw new IllegalArgumentException("El numero de alternativa debe ser positivo");
        }
        this.numeroAlternativa = numeroAlternativa;
    }

    public Long getId() { return id; }
    public Guia getGuia() { return guia; }
    public RutaReferencia getRutaReferencia() { return rutaReferencia; }
    public int getNumeroAlternativa() { return numeroAlternativa; }

    void reordenar(int nuevoNumeroAlternativa) {
        if (nuevoNumeroAlternativa < 1) {
            throw new IllegalArgumentException("El numero de alternativa debe ser positivo");
        }
        this.numeroAlternativa = nuevoNumeroAlternativa;
    }
}
